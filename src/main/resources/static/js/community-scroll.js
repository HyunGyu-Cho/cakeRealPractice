// 커뮤니티 무한스크롤 목록.
// 화면 하단 sentinel이 보일 때마다 /community/api/posts 를 커서 기반으로 이어서 조회한다.
(function () {
  const list = document.getElementById('post-list');
  const status = document.getElementById('scroll-status');
  const sentinel = document.getElementById('scroll-sentinel');
  if (!list || !status || !sentinel) return;

  const category = list.dataset.category;
  let cursor = null;
  let hasNext = true;
  let loading = false;

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

  async function loadNext() {
    if (loading || !hasNext) return;
    loading = true;
    status.textContent = '불러오는 중...';
    try {
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
      status.textContent = hasNext
        ? ''
        : (list.children.length ? '마지막 글입니다.' : '등록된 게시글이 없습니다.');
    } catch (error) {
      hasNext = false;
      status.textContent = '목록을 불러오지 못했습니다. 새로고침 해주세요.';
    } finally {
      loading = false;
    }
  }

  const observer = new IntersectionObserver(function (entries) {
    if (entries.some(function (entry) { return entry.isIntersecting; })) {
      loadNext();
    }
  }, { rootMargin: '200px' });

  observer.observe(sentinel);
  loadNext();
})();
