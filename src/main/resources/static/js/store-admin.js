document.addEventListener("DOMContentLoaded", () => {
  const timePattern = /^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/;

  // 사용자가 친 값("1300", "13:00", "오후 1:00", "1:00pm")을 24시간 "HH:mm"로 정규화한다.
  const parse24h = (raw) => {
    if (!raw) {
      return null;
    }
    const text = raw.trim();
    let meridiem = null;
    if (/오전/.test(text) || /\bam\b/i.test(text)) {
      meridiem = "am";
    } else if (/오후/.test(text) || /\bpm\b/i.test(text)) {
      meridiem = "pm";
    }

    const digits = text.replace(/[^0-9]/g, "");
    if (!digits) {
      return null;
    }

    let hours;
    let minutes;
    if (digits.length <= 2) {
      hours = parseInt(digits, 10);
      minutes = 0;
    } else if (digits.length === 3) {
      hours = parseInt(digits.slice(0, 1), 10);
      minutes = parseInt(digits.slice(1), 10);
    } else {
      hours = parseInt(digits.slice(0, digits.length - 2), 10);
      minutes = parseInt(digits.slice(-2), 10);
    }

    if (meridiem === "pm" && hours < 12) {
      hours += 12;
    } else if (meridiem === "am" && hours === 12) {
      hours = 0;
    }

    if (hours > 23 || minutes > 59) {
      return null;
    }
    return String(hours).padStart(2, "0") + ":" + String(minutes).padStart(2, "0");
  };

  // 저장용 24시간 값을 화면 표시용 한글 12시간 형식("오후 1:00")으로 바꾼다.
  const toKorean = (value) => {
    if (!timePattern.test(value)) {
      return "";
    }
    const [hours, minutes] = value.split(":").map(Number);
    const meridiem = hours < 12 ? "오전" : "오후";
    const hour12 = hours % 12 === 0 ? 12 : hours % 12;
    return `${meridiem} ${hour12}:${String(minutes).padStart(2, "0")}`;
  };

  // 시간 입력 UI: 화면에는 한글 12시간 표기, 서버에는 hidden 필드로 24시간 값을 보낸다.
  document.querySelectorAll(".time-input-with-picker").forEach((wrap) => {
    const hidden = wrap.querySelector("input[type='hidden']");
    const display = wrap.querySelector("input.time-display");
    const picker = wrap.querySelector("input[type='time']");
    const pickerButton = wrap.querySelector("button");

    if (!hidden || !display || !picker || !pickerButton) {
      return;
    }

    // 서버가 채워준 24시간 값을 화면/피커에 반영한다.
    const syncFromHidden = () => {
      display.value = toKorean(hidden.value);
      picker.value = timePattern.test(hidden.value) ? hidden.value : "";
    };

    // 사용자가 입력한 표시값을 정규화해 hidden/피커/표시값을 한 번에 맞춘다.
    const commitFromDisplay = () => {
      if (display.value.trim() === "") {
        hidden.value = "";
        picker.value = "";
        return;
      }
      const parsed = parse24h(display.value);
      if (parsed) {
        hidden.value = parsed;
        display.value = toKorean(parsed);
        picker.value = parsed;
      } else {
        // 형식이 잘못되면 서버 검증(필수값)에 걸리도록 hidden을 비운다.
        hidden.value = "";
        picker.value = "";
      }
    };

    syncFromHidden();

    display.addEventListener("change", commitFromDisplay);

    picker.addEventListener("change", () => {
      if (picker.value) {
        hidden.value = picker.value;
        display.value = toKorean(picker.value);
        display.focus();
      }
    });

    pickerButton.addEventListener("click", () => {
      if (typeof picker.showPicker === "function") {
        picker.showPicker();
        return;
      }
      picker.focus();
      picker.click();
    });

    // 값을 고치고 blur 하지 않은 채 제출해도 hidden이 최신값을 갖도록 한다.
    const form = wrap.closest("form");
    if (form) {
      form.addEventListener("submit", commitFromDisplay);
    }
  });
});
