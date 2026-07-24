package com.cakeshop.domain.store.mapper;

import com.cakeshop.domain.store.entity.Store;
import com.cakeshop.domain.store.entity.StoreBusinessHour;
import com.cakeshop.domain.store.entity.StoreHoliday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StoreMapper {

    Optional<Store> findStoreById(@Param("storeId") Long storeId);

    List<StoreBusinessHour> findBusinessHours(@Param("storeId") Long storeId);

    List<StoreHoliday> findHolidays(@Param("storeId") Long storeId);

    int updateStore(Store store);

    int upsertBusinessHour(StoreBusinessHour businessHour);

    boolean existsHolidayDate(@Param("storeId") Long storeId,
                              @Param("holidayDate") LocalDate holidayDate);

    int insertHoliday(StoreHoliday holiday);

    int deleteHoliday(@Param("storeId") Long storeId, @Param("holidayId") Long holidayId);
}
