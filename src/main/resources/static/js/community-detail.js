// 게시글 상세 — 좋아요 토글, 답글 대상 지정, 신고 폼 토글, 삭제 확인.
(function () {
  // 1) data-confirm 폼: 확인을 누를 때만 제출한다.
  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!window.confirm(form.dataset.confirm)) {
        event.preventDefault();
      }
    });
  });

  // 2) 신고 폼 열기/닫기
  const reportToggle = document.getElementById('report-toggle');
  const reportForm = document.getElementById('report-form');
  if (reportToggle && reportForm) {
    reportToggle.addEventListener('click', function () {
      reportForm.hidden = !reportForm.hidden;
      if (!reportForm.hidden) {
        reportForm.querySelector('textarea').focus();
      }
    });
  }

  // 3) 답글: 버튼을 누르면 댓글 폼의 parentCommentId를 채우고 안내를 띄운다.
  const commentForm = document.getElementById('comment-form');
  if (commentForm) {
    const parentInput = commentForm.querySelector('input[name="parentCommentId"]');
    const indicator = document.getElementById('reply-indicator');
    const target = document.getElementById('reply-target');
    const cancel = document.getElementById('reply-cancel');

    document.querySelectorAll('[data-reply-button]').forEach(function (button) {
      button.addEventListener('click', function () {
        parentInput.value = button.dataset.commentId;
        target.textContent = button.dataset.nickname;
        indicator.hidden = false;
        commentForm.querySelector('textarea').focus();
      });
    });

    if (cancel) {
      cancel.addEventListener('click', function () {
        parentInput.value = '';
        indicator.hidden = true;
      });
    }
  }

  // 4) 좋아요 토글
  const button = document.getElementById('like-button');
  if (!button || button.disabled) return;

  const csrfToken = document.querySelector('meta[name="_csrf"]');
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
  let busy = false;

  button.addEventListener('click', async function () {
    if (busy) return;
    busy = true;
    try {
      const headers = {};
      if (csrfToken && csrfHeader && csrfHeader.content) {
        headers[csrfHeader.content] = csrfToken.content;
      }
      const response = await fetch('/community/api/posts/' + button.dataset.postId + '/like', {
        method: 'POST',
        headers: headers
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const result = await response.json();
      button.dataset.liked = String(result.liked);
      button.classList.toggle('is-active', result.liked);
      button.textContent = (result.liked ? '♥' : '♡') + ' 좋아요 ' + result.likeCount;
    } catch (error) {
      alert('좋아요 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      busy = false;
    }
  });
})();
