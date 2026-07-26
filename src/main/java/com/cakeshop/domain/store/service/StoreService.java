package com.cakeshop.domain.store.service;

import com.cakeshop.domain.store.dto.form.StoreHolidayForm;
import com.cakeshop.domain.store.dto.form.StoreUpdateForm;
import com.cakeshop.domain.store.dto.view.StoreHolidayView;
import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.domain.store.dto.view.StoreView;
import com.cakeshop.domain.store.error.StoreErrorCode;
import com.cakeshop.domain.store.mapper.StoreMapper;
import com.cakeshop.domain.store.entity.Store;
import com.cakeshop.domain.store.entity.StoreBusinessHour;
import com.cakeshop.domain.store.entity.StoreHoliday;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import com.cakeshop.global.infra.ImageValidator;
import com.cakeshop.global.infra.StoredFileCleanup;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoreService {

    private static final Logger log = LoggerFactory.getLogger(StoreService.class);

    // 현재 서비스는 단일 매장을 운영하므로 초기 SQL에서 보장한 대표 행을 사용한다.
    public static final long DEFAULT_STORE_ID = 1L;
    /** 일반 주문 픽업 예약 창. 결제 시 재고가 묶이므로 짧게 유지한다. */
    public static final int DEFAULT_PICKUP_WINDOW_DAYS = 14;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // 매장 도메인이 저장하는 이미지의 저장 하위 디렉토리 (/{directory}/{yyyyMM}/{uuid}.{ext})
    private static final String IMAGE_DIRECTORY = "store";

    private final StoreMapper storeMapper;
    private final FileStorageClient fileStorageClient;
    private final ImageValidator imageValidator;
    private final StoredFileCleanup storedFileCleanup;
    /** order가 끼워주는 예약 현황 조회. 없으면 정원·휴무일 충돌 검사를 건너뛴다(테스트·부분 기동 대비). */
    private final PickupReservationPort pickupReservationPort;
    private final Clock clock;

    @Autowired
    public StoreService(StoreMapper storeMapper, FileStorageClient fileStorageClient,
                        PickupReservationPort pickupReservationPort,
                        ImageValidator imageValidator, StoredFileCleanup storedFileCleanup) {
        this(storeMapper, fileStorageClient, pickupReservationPort,
            imageValidator, storedFileCleanup, Clock.systemDefaultZone());
    }

    public StoreService(StoreMapper storeMapper, FileStorageClient fileStorageClient,
                        PickupReservationPort pickupReservationPort,
                        ImageValidator imageValidator, StoredFileCleanup storedFileCleanup,
                        Clock clock) {
        this.storeMapper = storeMapper;
        this.fileStorageClient = fileStorageClient;
        this.pickupReservationPort = pickupReservationPort;
        this.imageValidator = imageValidator;
        this.storedFileCleanup = storedFileCleanup;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StoreView getStoreView() {
        Store store = findDefaultStore();
        List<StoreBusinessHour> hours = storeMapper.findBusinessHours(DEFAULT_STORE_ID);
        List<StoreHoliday> holidays = storeMapper.findHolidays(DEFAULT_STORE_ID);
        List<StoreHolidayView> holidayViews = holidays.stream()
            .map(holiday -> new StoreHolidayView(
                holiday.getId(), holiday.getHolidayDate(), holiday.getReason()))
            .toList();
        Map<DayOfWeek, StoreBusinessHour> hourMap = toHourMap(hours);

        StoreBusinessHour weekday = representativeHour(hourMap,
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        StoreBusinessHour weekend = representativeHour(hourMap, List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
        Set<DayOfWeek> closedDays = EnumSet.noneOf(DayOfWeek.class);
        hours.stream().filter(StoreBusinessHour::isClosed)
            .map(StoreBusinessHour::getDayOfWeek)
            .forEach(closedDays::add);

        return new StoreView(
            store.getId(), store.getName(), store.getDescription(), store.getImageUrl(),
            store.getAddress(), store.getPhone(),
            weekday.getOpenTime(), weekday.getCloseTime(), weekend.getOpenTime(), weekend.getCloseTime(),
            Set.copyOf(closedDays), store.getPickupPlace(), store.getPickupStartTime(), store.getPickupEndTime(),
            store.getPickupIntervalMinutes(), holidayViews
        );
    }

    @Transactional(readOnly = true)
    public StorePublicView getPublicStore() {
        StoreView store = getStoreView();
        String businessHours = "평일 %s / 주말 %s".formatted(
            formatHourRange(store.weekdayOpenTime(), store.weekdayCloseTime()),
            formatHourRange(store.weekendOpenTime(), store.weekendCloseTime())
        );
        String closedDays = store.closedDays().isEmpty()
            ? "정기 휴무 없음"
            : store.closedDays().stream()
                .sorted()
                .map(this::toKoreanDay)
                .reduce((left, right) -> left + "·" + right)
                .orElse("") + "요일 휴무";

        return new StorePublicView(
            store.name(), store.description(), store.imageUrl(), store.address(), store.phone(),
            businessHours, closedDays,
            store.pickupPlace(), "%s ~ %s".formatted(
                formatTime(store.pickupStartTime()), formatTime(store.pickupEndTime()))
        );
    }

    /**
     * 주문 도메인이 사용하는 픽업 슬롯 공개 계약.
     * 준비 기간 이후부터 예약 창(기본 {@value #DEFAULT_PICKUP_WINDOW_DAYS}일) 이내의
     * 영업일·운영시간 슬롯만 반환한다.
     *
     * <p><b>이미 예약된 슬롯은 여기서 걸러내지 않는다.</b> store가 orders를 조회하면
     * order → store 위에 store → order가 얹혀 순환이 되므로, 정원 필터는 주문을 소유한
     * order 도메인이 이 결과에 건다.
     */
    @Transactional(readOnly = true)
    public List<LocalDateTime> getAvailablePickupSlots(LocalDate date, int preparationDays) {
        return getAvailablePickupSlots(date, preparationDays, DEFAULT_PICKUP_WINDOW_DAYS);
    }

    /**
     * 예약 창 상한을 호출측이 지정하는 변형. 일반 주문은 재고가 묶이므로 기본 14일을 쓰고,
     * 재고를 관리하지 않는 주문제작은 더 긴 상한을 넘긴다(order-custom 스펙 6장 규칙 9).
     */
    @Transactional(readOnly = true)
    public List<LocalDateTime> getAvailablePickupSlots(LocalDate date, int preparationDays,
                                                       int windowDays) {
        if (date == null || preparationDays < 0 || windowDays < 0
            || !isDateInPickupWindow(date, preparationDays, windowDays)) {
            return List.of();
        }

        Store store = findDefaultStore();
        StoreBusinessHour businessHour = storeMapper.findBusinessHours(DEFAULT_STORE_ID).stream()
            .filter(hour -> hour.getDayOfWeek() == date.getDayOfWeek())
            .findFirst()
            .orElse(null);
        if (businessHour == null || businessHour.isClosed()
            || storeMapper.findHolidays(DEFAULT_STORE_ID).stream()
                .anyMatch(holiday -> date.equals(holiday.getHolidayDate()))) {
            return List.of();
        }

        LocalTime start = later(store.getPickupStartTime(), businessHour.getOpenTime());
        LocalTime end = earlier(store.getPickupEndTime(), businessHour.getCloseTime());
        Integer interval = store.getPickupIntervalMinutes();
        if (start == null || end == null || interval == null || interval < 1 || !start.isBefore(end)) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime configuredEnd = LocalDateTime.of(date, store.getPickupEndTime());
        for (LocalDateTime slot = LocalDateTime.of(date, store.getPickupStartTime());
             slot.isBefore(configuredEnd);
             slot = slot.plusMinutes(interval)) {
            LocalTime time = slot.toLocalTime();
            if (time.isBefore(start) || !time.isBefore(end)) {
                continue;
            }
            if (date.equals(now.toLocalDate()) && !slot.isAfter(now)) {
                continue;
            }
            slots.add(slot);
        }
        // 슬롯 정원은 1건이다. 이미 픽업이 잡힌 시각은 목록에서 뺀다.
        if (pickupReservationPort != null && !slots.isEmpty()) {
            Set<LocalDateTime> reserved = pickupReservationPort.findReservedPickupAts(List.of(date));
            slots.removeIf(reserved::contains);
        }
        return List.copyOf(slots);
    }

    @Transactional(readOnly = true)
    public void validatePickupAt(LocalDateTime pickupAt, int preparationDays) {
        validatePickupAt(pickupAt, preparationDays, DEFAULT_PICKUP_WINDOW_DAYS);
    }

    /**
     * 실패 사유를 구분해 알린다. 휴무일·정기 휴무는 고객이 다른 날짜를 고르면 되는 상황이라
     * "선택할 수 없다"는 뭉뚱그린 메시지보다 사유를 그대로 알려주는 편이 낫다.
     */
    @Transactional(readOnly = true)
    public void validatePickupAt(LocalDateTime pickupAt, int preparationDays, int windowDays) {
        if (pickupAt == null) {
            throw new BusinessException(StoreErrorCode.INVALID_PICKUP_AT);
        }
        LocalDate date = pickupAt.toLocalDate();
        if (isClosedDay(date)) {
            throw new BusinessException(StoreErrorCode.PICKUP_DATE_CLOSED);
        }
        if (!getAvailablePickupSlots(date, preparationDays, windowDays).contains(pickupAt)) {
            // 정원이 차서 빠진 것인지, 애초에 슬롯이 아닌지 구분해 알린다.
            if (pickupReservationPort != null
                && pickupReservationPort.findReservedPickupAts(List.of(date)).contains(pickupAt)) {
                throw new BusinessException(StoreErrorCode.PICKUP_SLOT_TAKEN);
            }
            throw new BusinessException(StoreErrorCode.INVALID_PICKUP_AT);
        }
    }

    /** 정기 휴무(요일)이거나 특정 휴무일인지. */
    @Transactional(readOnly = true)
    public boolean isClosedDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        StoreBusinessHour businessHour = storeMapper.findBusinessHours(DEFAULT_STORE_ID).stream()
            .filter(hour -> hour.getDayOfWeek() == date.getDayOfWeek())
            .findFirst()
            .orElse(null);
        if (businessHour == null || businessHour.isClosed()) {
            return true;
        }
        return storeMapper.findHolidays(DEFAULT_STORE_ID).stream()
            .anyMatch(holiday -> date.equals(holiday.getHolidayDate()));
    }

    @Transactional(readOnly = true)
    public boolean isPickupAtAvailable(LocalDateTime pickupAt, int preparationDays) {
        try {
            validatePickupAt(pickupAt, preparationDays);
            return true;
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == StoreErrorCode.INVALID_PICKUP_AT
                || exception.getErrorCode() == StoreErrorCode.PICKUP_DATE_CLOSED) {
                return false;
            }
            throw exception;
        }
    }

    private boolean isDateInPickupWindow(LocalDate date, int preparationDays, int windowDays) {
        LocalDate today = LocalDate.now(clock);
        return !date.isBefore(today.plusDays(preparationDays))
            && !date.isAfter(today.plusDays(windowDays));
    }

    private LocalTime later(LocalTime left, LocalTime right) {
        if (left == null || right == null) {
            return null;
        }
        return left.isAfter(right) ? left : right;
    }

    private LocalTime earlier(LocalTime left, LocalTime right) {
        if (left == null || right == null) {
            return null;
        }
        return left.isBefore(right) ? left : right;
    }

    /** 기본 정보와 7개 요일을 한 트랜잭션으로 저장해 일부만 반영되는 상태를 막는다. */
    @Transactional
    public void updateStore(StoreUpdateForm form, MultipartFile image) {
        Store store = findDefaultStore();
        store.setName(form.getName().trim());
        store.setDescription(trimToNull(form.getDescription()));
        store.setAddress(form.getAddress().trim());
        store.setPhone(form.getPhone().trim());
        store.setPickupPlace(form.getPickupPlace().trim());
        store.setPickupStartTime(form.getPickupStartTime());
        store.setPickupEndTime(form.getPickupEndTime());
        store.setPickupIntervalMinutes(form.getPickupIntervalMinutes());

        // 새 이미지가 올라온 경우에만 교체한다. 첨부가 없으면 기존 이미지를 그대로 유지한다.
        String previousImageUrl = store.getImageUrl();
        boolean imageReplaced = image != null && !image.isEmpty();
        String newImageUrl = null;
        if (imageReplaced) {
            validateImage(image);
            newImageUrl = fileStorageClient.store(image, IMAGE_DIRECTORY);
            store.setImageUrl(newImageUrl);
        }

        boolean transactionSynchronized =
            imageReplaced && storedFileCleanup.registerReplace(previousImageUrl, newImageUrl);

        if (storeMapper.updateStore(store) != 1) {
            if (imageReplaced && !transactionSynchronized) {
                storedFileCleanup.deleteNow(newImageUrl);
            }
            throw new BusinessException(StoreErrorCode.UPDATE_FAILED);
        }

        // DB 작업이 모두 끝난 뒤에만 파일 정리를 확정한다.
        try {
            for (DayOfWeek day : DayOfWeek.values()) {
                boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
                boolean closed = form.getClosedDays().contains(day);
                StoreBusinessHour hour = new StoreBusinessHour();
                hour.setStoreId(DEFAULT_STORE_ID);
                hour.setDayOfWeek(day);
                hour.setClosed(closed);
                // DB 제약(chk_store_business_hour_time): 휴무일은 영업시간이 NULL 이어야 한다.
                hour.setOpenTime(closed ? null : (weekend ? form.getWeekendOpenTime() : form.getWeekdayOpenTime()));
                hour.setCloseTime(closed ? null : (weekend ? form.getWeekendCloseTime() : form.getWeekdayCloseTime()));
                storeMapper.upsertBusinessHour(hour);
            }
        } catch (RuntimeException exception) {
            if (imageReplaced && !transactionSynchronized) {
                storedFileCleanup.deleteNow(newImageUrl);
            }
            throw exception;
        }

        // 프록시를 거치지 않는 단위 테스트 같은 비트랜잭션 호출도 파일 정합성을 지킨다.
        if (imageReplaced && !transactionSynchronized) {
            storedFileCleanup.deleteNow(previousImageUrl);
        }
    }

    @Transactional
    public void addHoliday(StoreHolidayForm form) {
        findDefaultStore();
        if (storeMapper.existsHolidayDate(DEFAULT_STORE_ID, form.getHolidayDate())) {
            throw new BusinessException(StoreErrorCode.HOLIDAY_ALREADY_EXISTS);
        }
        // 이미 픽업이 잡힌 날을 휴무로 만들면 주문과 매장 일정이 조용히 어긋난다.
        // 관리자가 주문을 먼저 정리하도록 막고, 몇 건인지 함께 알린다.
        if (pickupReservationPort != null) {
            long reserved = pickupReservationPort.countReservedPickups(form.getHolidayDate());
            if (reserved > 0) {
                throw new BusinessException(StoreErrorCode.HOLIDAY_HAS_PICKUP,
                    "이 날짜에 픽업 예정 주문이 " + reserved + "건 있어 휴무일로 지정할 수 없습니다."
                        + " 주문을 먼저 처리하거나 픽업 일시를 변경해 주세요.");
            }
        }

        StoreHoliday holiday = new StoreHoliday();
        holiday.setStoreId(DEFAULT_STORE_ID);
        holiday.setHolidayDate(form.getHolidayDate());
        holiday.setReason(form.getReason().trim());
        storeMapper.insertHoliday(holiday);
    }

    @Transactional
    public void deleteHoliday(long holidayId) {
        if (storeMapper.deleteHoliday(DEFAULT_STORE_ID, holidayId) != 1) {
            throw new BusinessException(StoreErrorCode.HOLIDAY_NOT_FOUND);
        }
    }

    private Store findDefaultStore() {
        return storeMapper.findStoreById(DEFAULT_STORE_ID)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
    }

    private Map<DayOfWeek, StoreBusinessHour> toHourMap(List<StoreBusinessHour> hours) {
        Map<DayOfWeek, StoreBusinessHour> result = new EnumMap<>(DayOfWeek.class);
        hours.forEach(hour -> result.put(hour.getDayOfWeek(), hour));
        return result;
    }

    // 평일/주말 대표 영업시간은 '휴무가 아닌' 첫 요일에서 가져온다.
    // 월요일 하나만 정기 휴무여도 평일 시간(화~금)이 통째로 사라지지 않게 하기 위함이다.
    private StoreBusinessHour representativeHour(Map<DayOfWeek, StoreBusinessHour> hours, List<DayOfWeek> candidates) {
        StoreBusinessHour fallback = null;
        for (DayOfWeek day : candidates) {
            StoreBusinessHour hour = hours.get(day);
            if (hour == null) {
                continue;
            }
            if (fallback == null) {
                fallback = hour; // 후보가 모두 휴무면 시간이 null인 행이라도 대표로 둔다(공개 화면에서 '휴무'로 표시).
            }
            if (!hour.isClosed()) {
                return hour;
            }
        }
        if (fallback == null) {
            // 초기 SQL이 빠졌거나 데이터가 손상된 경우 화면에 잘못된 기본값을 만들지 않는다.
            throw new BusinessException(StoreErrorCode.NOT_FOUND);
        }
        return fallback;
    }

    private String formatTime(LocalTime time) {
        return time.format(TIME_FORMATTER);
    }

    // 평일/주말이 모두 휴무라 대표 시간이 없을 수 있으므로 null을 '휴무'로 표시한다.
    private String formatHourRange(LocalTime open, LocalTime close) {
        if (open == null || close == null) {
            return "휴무";
        }
        return "%s ~ %s".formatted(formatTime(open), formatTime(close));
    }

    /**
     * content type 은 클라이언트가 보내는 값이라 그것만으로는 부족하다 — 공통 검증기가 실제 파일
     * 머리 바이트까지 확인한다. 여기서는 판정 결과를 매장 오류 코드로 옮기기만 한다.
     */
    private void validateImage(MultipartFile image) {
        if (imageValidator.validate(image) != null) {
            throw new BusinessException(StoreErrorCode.INVALID_IMAGE);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toKoreanDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }
}
