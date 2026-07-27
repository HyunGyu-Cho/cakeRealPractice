// 커뮤니티 무한스크롤 목록.
// 화면 하단 sentinel이 보일 때마다 /community/api/posts 를 커서 기반으로 이어서 조회한다.
(function () {
  const list = document.getElementById('post-list');
  const status = document.getElementById('scroll-status');
  const sentinel = document.getElementById('scroll-sentinel');
  if (!list || !status || !sentinel) return;

  // 옵저버 rootMargin과 "sentinel이 아직 화면 안인가" 판정에 같은 값을 쓴다.
  const ROOT_MARGIN_PX = 200;
  // 뷰포트를 채우려고 연속 로드하는 최대 배치 수(무한 루프 방지).
  const MAX_AUTO_FILL = 20;

  const category = list.dataset.category;
  let cursor = null;
  let hasNext = true;
  let loading = false;
  let failed = false;

  function badgeClass(code) {
    if (code === 'REVIEW') return 'badge badge--info';
    if (code === 'RECIPE') return 'badge badge--success';
    return 'badge';
  }

  // 서버 데이터는 항상 textContent로만 넣는다(HTML 문자열 조립 금지 — XSS 방지).
  function renderPost(post) {
    const article = document.createElement('article');
    article.className = 'panel';

    const top = document.createElement('div');
    top.className = 'cluster cluster--between';

    const left = document.createElement('div');
    left.className = 'cluster';
    const badge = document.createElement('span');
    badge.className = badgeClass(post.categoryCode);
    badge.textContent = post.categoryName;
    const link = document.createElement('a');
    link.href = '/community/' + post.id;
    const title = document.createElement('strong');
    title.textContent = post.title;
    link.appendChild(title);
    left.append(badge, link);

    const likes = document.createElement('span');
    likes.className = 'text-muted';
    likes.textContent = '좋아요 ' + post.likeCount;
    top.append(left, likes);

    const meta = document.createElement('p');
    meta.className = 'text-muted';
    meta.textContent = post.nickname + ' · ' + post.createdDate + ' · 댓글 ' + post.commentCount;

    article.append(top, meta);
    return article;
  }

  // sentinel이 아직 화면(+rootMargin) 안에 있으면 더 채워야 한다.
  // IntersectionObserver는 "교차 상태가 바뀔 때"만 발화하므로, 한 배치를 붙여도 여전히
  // 교차 상태면 콜백이 다시 오지 않는다. 그래서 여기서 직접 판정해 이어서 로드한다.
  function needsMore() {
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
    return sentinel.getBoundingClientRect().top <= viewportHeight + ROOT_MARGIN_PX;
  }

  function showRetry() {
    status.textContent = '목록을 불러오지 못했습니다. ';
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.className = 'btn';
    retry.textContent = '다시 시도';
    retry.addEventListener('click', function () {
      failed = false;
      loadNext();
    });
    status.appendChild(retry);
  }

  // 한 배치를 조회해 붙인다. 성공하면 true.
  async function fetchBatch() {
    const params = new URLSearchParams();
    if (category) params.set('category', category);
    if (cursor !== null) params.set('cursor', cursor);
    const response = await fetch('/community/api/posts?' + params.toString());
    if (!response.ok) throw new Error('HTTP ' + response.status);
    const slice = await response.json();

    slice.posts.forEach(function (post) {
      list.appendChild(renderPost(post));
    });
    hasNext = slice.hasNext;
    cursor = slice.nextCursor;
    return slice.posts.length;
  }

  async function loadNext() {
    if (loading || failed || !hasNext) return;
    loading = true;
    status.textContent = '불러오는 중...';
    try {
      // 뷰포트가 찰 때까지(= sentinel이 화면 밖으로 밀려날 때까지) 이어서 로드한다.
      for (let i = 0; i < MAX_AUTO_FILL; i++) {
        const loaded = await fetchBatch();
        if (!hasNext || loaded === 0 || !needsMore()) break;
      }
      status.textContent = hasNext
        ? ''
        : (list.children.length ? '마지막 글입니다.' : '등록된 게시글이 없습니다.');
    } catch (error) {
      // hasNext는 유지한다 — 다시 시도 버튼으로 이어서 받을 수 있게.
      failed = true;
      showRetry();
    } finally {
      loading = false;
    }
  }

  const observer = new IntersectionObserver(function (entries) {
    if (entries.some(function (entry) { return entry.isIntersecting; })) {
      loadNext();
    }
  }, { rootMargin: ROOT_MARGIN_PX + 'px' });

  observer.observe(sentinel);
  loadNext();
})();
