/*
 * 토스 결제창 연동. 결제 폼 제출을 가로채 결제창을 열고, 승인 결과는 서버 콜백
 * (/orders/payment/success · /orders/payment/fail)에서 처리한다.
 *
 * 금액과 주문번호는 서버가 준비 단계에서 정한 값을 그대로 넘긴다. 여기서 계산하지 않는다.
 */
(function () {
  var form = document.getElementById('payment-form');
  if (!form || form.dataset.provider !== 'toss') {
    return;
  }

  // 우리 결제수단 값 → 토스 결제창 종류. 서버에 저장되는 값은 우리 쪽 값 그대로다.
  var METHODS = {
    CARD: 'CARD',
    KAKAO: 'CARD',
    NAVER: 'CARD',
    TOSS: 'CARD',
    BANK: 'TRANSFER'
  };

  var widget = TossPayments(form.dataset.clientKey).payment({ customerKey: TossPayments.ANONYMOUS });

  form.addEventListener('submit', function (event) {
    event.preventDefault();

    var selected = form.querySelector('input[name="method"]:checked');
    var agreed = form.querySelector('input[name="refundPolicyAgreed"]');
    if (!selected) {
      window.alert('결제 수단을 선택해 주세요.');
      return;
    }
    if (agreed && !agreed.checked) {
      window.alert('취소·환불 규정에 동의해 주세요.');
      return;
    }

    var origin = window.location.origin;
    // 우리가 붙인 쿼리 파라미터는 콜백에 그대로 돌아온다 — 승인 후 저장할 결제수단과 쿠폰 선택.
    var successUrl = origin + (form.dataset.successUrl || '/orders/payment/success')
      + '?method=' + encodeURIComponent(selected.value);
    var coupon = form.querySelector('input[name="memberCouponId"]');
    if (coupon && coupon.value) {
      successUrl += '&memberCouponId=' + encodeURIComponent(coupon.value);
    }

    widget.requestPayment({
      method: METHODS[selected.value] || 'CARD',
      amount: { currency: 'KRW', value: Number(form.dataset.amount) },
      orderId: form.dataset.orderId,
      orderName: form.dataset.orderName,
      successUrl: successUrl,
      failUrl: origin + (form.dataset.failUrl || '/orders/payment/fail')
    }).catch(function (error) {
      // 고객이 결제창을 닫은 경우도 여기로 온다. 준비된 결제는 대조 배치가 정리한다.
      if (error && error.code !== 'USER_CANCEL') {
        window.alert(error.message || '결제를 시작하지 못했습니다.');
      }
    });
  });
})();
