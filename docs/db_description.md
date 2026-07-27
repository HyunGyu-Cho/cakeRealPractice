# DB가 어떻게 돌아가는지 (처음 보는 사람용)

이 문서는 **DB를 거의 다뤄본 적 없는 사람**이 이 프로젝트의 데이터베이스를 이해하고,
자기 PC에서 직접 굴려 보는 것까지를 목표로 한다. 위에서부터 순서대로 따라가면 된다.

이미 익숙한 사람은 [`docs/sql/README.md`](sql/README.md)부터 보면 된다.

---

## 1. 우리 프로젝트에서 DB가 하는 일

회원가입한 사람, 등록된 케이크, 들어온 주문 — 이런 것들은 **앱을 껐다 켜도 남아 있어야 한다.**
그래서 프로그램 메모리가 아니라 **데이터베이스(DB)** 라는 별도 프로그램에 저장한다.
우리가 쓰는 DB 프로그램은 **MariaDB**다.

DB 안은 엑셀 시트 여러 장이라고 생각하면 얼추 맞다. 시트 한 장을 **테이블(table)** 이라고 부른다.

| 테이블 이름 | 담는 것 |
|---|---|
| `members` | 회원 |
| `products` | 케이크 상품 |
| `orders` | 주문 |
| `reviews` | 후기 |

이런 테이블이 지금 **38개** 있다.

---

## 2. 단어 3개만 알면 된다

### 스키마 (schema)

**테이블의 뼈대.** 엑셀로 치면 "이 시트에는 이름·전화번호·가입일 칸이 있다"는 정보다.
데이터(내용)가 아니라 칸의 구조를 말한다.

### 마이그레이션 (migration)

**뼈대를 바꾸는 작업 지시서 한 장.** 예를 들어 "`members` 시트에 생일 칸을 추가해라" 같은
내용이 SQL이라는 언어로 적힌 파일이다. 한 번 만들면 번호가 붙고, 그 번호 순서대로 실행된다.

### Flyway

**지시서를 순서대로 대신 실행해 주는 도구.** 두 가지를 해 준다.

1. 아직 실행 안 한 지시서만 골라서 순서대로 실행
2. **어디까지 실행했는지 DB에 적어 둠** (`flyway_schema_history`라는 테이블에 기록된다)

우리 프로젝트에서는 **앱을 실행하면 Flyway가 자동으로 돌아간다.** 따로 할 일이 없다.

---

## 3. 예전엔 어땠나 (왜 이렇게 바뀌었는지)

예전에는 SQL 파일을 팀원이 **각자 손으로 순서대로 실행**했다. 문제는 이랬다.

- 내 PC가 몇 번 파일까지 실행했는지 **아무도 몰랐다.** DB에 기록이 없으니 기억에 의존
- 하나 빠뜨리면 나만 다른 구조를 갖게 되고, "왜 내 컴에서만 에러 나지?"가 됨

Flyway를 쓰면 이 두 가지가 그냥 사라진다. 기록이 DB에 남으니까.

바꾼 배경을 더 알고 싶으면 [`docs/flyway-migration-proposal.md`](flyway-migration-proposal.md).

---

## 4. 처음 세팅하기

### 4-1. MariaDB 설치

각자 PC에 **MariaDB 11.4**를 설치한다. 설치 중 물어보는 root 비밀번호는 적어 둔다.

### 4-2. 빈 데이터베이스 만들기

테이블은 만들 필요 없다. **껍데기만** 만들면 나머지는 앱이 알아서 채운다.

```powershell
mariadb -u root -p -e "CREATE DATABASE cakeshop DEFAULT CHARACTER SET utf8mb4;"
```

### 4-3. 접속 정보 적기

프로젝트 맨 위 폴더의 `.env_sample` 파일을 복사해 이름을 `.env`로 바꾸고, 자기 값으로 고친다.

```
LOCAL_DB_HOST=localhost
LOCAL_DB_PORT=3307        ← MariaDB 설치할 때 정한 포트 (보통 3306)
LOCAL_DB_DATABASE=cakeshop
LOCAL_DB_USERNAME=root
LOCAL_DB_PASSWORD=여기에_자기_비밀번호
```

> `.env`는 비밀번호가 들어 있어서 **깃허브에 올리면 안 된다.** 자동으로 제외되게 설정돼 있다.

### 4-4. 앱 실행

```powershell
.\gradlew.bat bootRun
```

이 한 줄이 테이블 38개를 만들고, 기본 데이터와 샘플 데이터까지 다 넣는다.

### 4-5. 잘 됐는지 확인

실행 중 뜨는 글자 중에 이런 줄들이 보이면 성공이다.

```
Migrating schema `cakeshop` to version "1 - baseline schema"
Migrating schema `cakeshop` to version "2 - post categories seed"
Migrating schema `cakeshop` to version "3 - drop unused cart item tables"
Migrating schema `cakeshop` with repeatable migration "dev seed"
Successfully applied 4 migrations to schema `cakeshop`, now at version v3
Tomcat started on port 8080 (http)
Started CakeshopApplication in 12.128 seconds
```

브라우저에서 **http://localhost:8080** 을 열어 화면이 나오면 끝이다.
전체 화면 목록은 http://localhost:8080/screens 에서 볼 수 있다.

관리자 화면(`/admin`으로 시작하는 주소)은 로그인이 필요하다.

- 아이디: `admin@cakeshop.local`
- 비밀번호: `Admin1234!`

**두 번째 실행부터는** 이미 다 만들어져 있으므로 이 줄이 뜬다. 정상이다.

```
Schema `cakeshop` is up to date. No migration necessary.
```

앱을 끌 때는 실행 중인 창에서 **`Ctrl + C`** 를 누른다. 안 끄고 또 실행하면 8080번을
이미 쓰고 있다며 에러가 난다(9번 항목 참고).

---

## 5. 파일이 어디 있고 뭘 하나

```
src/main/resources/db/
├── migration/      ← 모든 사람, 모든 환경에 적용된다
│   ├── V1__baseline_schema.sql              테이블 38개를 만드는 파일
│   ├── V2__post_categories_seed.sql         게시판 종류(후기·질문·레시피·자유)
│   └── V3__drop_unused_cart_item_tables.sql 안 쓰는 테이블 정리
└── seed/           ← 내 PC(local)에서만 적용된다
    └── R__dev_seed.sql                      샘플 계정, 샘플 케이크 상품
```

**둘의 차이가 중요하다.**

| | `db/migration` | `db/seed` |
|---|---|---|
| 언제 적용되나 | 항상 | 내 PC에서만 |
| 들어 있는 것 | 테이블 구조, 없으면 화면이 깨지는 기본값 | 눈으로 확인하려고 넣어 둔 가짜 데이터 |
| 예시 | 케이크 분류 4종, 매장 정보 | 딸기 생크림 케이크, `admin@cakeshop.local` 계정 |

> **왜 관리자 계정이 "가짜 데이터"에 있나?**
> `Admin1234!`의 암호화된 값이 깃허브에 그대로 공개돼 있기 때문이다. 아무나 볼 수 있는
> 비밀번호라 **연습용으로만** 써야 하고, 팀 공용 서버에는 들어가면 안 된다.
> 그래서 아예 "내 PC에서만 적용되는" 폴더에 넣어 두고 설정으로 막았다.

### 파일 이름 규칙

- `V숫자__설명.sql` — 밑줄(`_`)이 **2개**다. 하나면 Flyway가 못 알아본다
- `R__설명.sql` — `R`은 반복(Repeatable). 내용이 바뀌면 **다시 실행**된다

`docs/sql/legacy/` 폴더에도 옛날 SQL 파일들이 있는데, **더 이상 쓰지 않는 기록 보관용**이다.
실행하면 안 된다.

---

## 6. 테이블을 새로 만들거나 칸을 추가하고 싶을 때

예를 들어 회원에게 "생일" 칸을 추가한다고 하자.

### 1단계 — 다음 번호 확인

`src/main/resources/db/migration/` 폴더를 보고 가장 큰 번호를 찾는다.
지금은 `V3`까지 있으니 다음은 **`V4`** 다.

### 2단계 — 파일 만들기

`src/main/resources/db/migration/V4__add_member_birthday.sql`

```sql
ALTER TABLE `members` ADD COLUMN `birthday` DATE NULL;
```

### 3단계 — 앱 재시작

```powershell
.\gradlew.bat bootRun
```

로그에 이렇게 뜨면 반영된 것이다.

```
Migrating schema `cakeshop` to version "4 - add member birthday"
```

**끝이다.** 다른 파일을 고칠 필요도, DB에 직접 접속할 필요도 없다.
팀원은 이 파일을 받아서 앱을 켜기만 하면 똑같이 반영된다.

---

## 7. 절대 하면 안 되는 것 2가지

### ① 이미 올린 마이그레이션 파일 고치기

`V1`, `V2`, `V3`처럼 **이미 깃허브에 올라간 파일은 절대 수정하지 않는다.**
고칠 게 있으면 `V4`를 새로 만들어 거기서 바꾼다.

왜냐면 Flyway가 파일마다 지문 같은 값(체크섬)을 저장해 두는데, 파일을 고치면 지문이
달라진다. 그러면 **이미 그 파일을 실행한 팀원의 앱이 안 켜진다.**

### ② DB에 직접 접속해서 테이블 고치기

손으로 고치면 Flyway는 그 사실을 모른다. 내 DB만 남들과 다른 상태가 되고,
나중에 원인 찾기가 아주 어려워진다. 변경은 **항상 마이그레이션 파일로** 한다.

---

## 8. 팀 공용 서버(RDS)는 어떻게 되나

공용 서버는 **자동 반영이 꺼져 있다.** 아무나 앱을 켰다가 공용 DB 구조가 바뀌면 위험해서다.
반영이 필요하면 담당자가 아래처럼 한 번만 켠다. 평소에는 신경 쓸 필요 없다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=rds --spring.flyway.enabled=true"
```

---

## 9. 에러가 났을 때

### `Found non-empty schema(s) ... but no schema history table`

**뜻**: "테이블은 있는데 언제 만들었는지 기록이 없다. 뭘 해야 할지 모르겠다"

Flyway를 도입하기 전(2026-07-27 이전)에 만든 DB이거나, 손으로 만든 DB다.
아래처럼 한 번만 실행하면 "지금 상태를 시작점으로 인정"하고 넘어간다.

```powershell
.\gradlew.bat bootRun --args="--spring.flyway.baseline-on-migrate=true"
```

잘 모르겠으면 **빈 DB를 새로 만드는 게 더 안전하다**(4번 항목).

### `Migration checksum mismatch`

**뜻**: "이미 실행한 파일의 내용이 바뀌었다"

7번의 ①을 어긴 것이다. 누군가 `V1`~`V3` 중 하나를 고쳤다. 그 파일을 원래대로 되돌리고,
바꾸고 싶은 내용은 새 `V4` 파일로 옮긴다.

### `Port 8080 was already in use`

**뜻**: "8080번 자리를 이미 다른 앱이 쓰고 있다"

앱이 이미 켜져 있는 것이다. 실행 중인 창에서 `Ctrl + C`로 끄고 다시 실행한다.

---

## 10. 더 알고 싶으면

| 문서 | 내용 |
|---|---|
| [`docs/sql/README.md`](sql/README.md) | 마이그레이션 규칙 상세 |
| [`README.md`](../README.md) | 프로젝트 전체 실행 방법 |
| [`docs/flyway-migration-proposal.md`](flyway-migration-proposal.md) | 왜 Flyway로 바꿨는지 |
| [`CLAUDE.md`](../CLAUDE.md) | 팀 규칙 전체 |
