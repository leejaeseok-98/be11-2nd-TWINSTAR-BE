# TwinStar 로깅 가이드

## 개요
TwinStar 프로젝트의 로깅 시스템은 Logback을 기반으로 구성되어 있으며, 다음과 같은 기능을 제공합니다:
- 콘솔 및 파일 기반 로깅
- 로그 레벨별 분리 (INFO, ERROR)
- 카테고리별 로그 파일 분리 (API, SQL, ERROR)
- 일별 로그 파일 롤링
- 파일 크기 기반 로그 분할

## 로그 파일 위치
로그 파일은 프로젝트 루트의 `logs` 디렉토리에 저장됩니다:

```
logs/
├── twinstar.log              # 전체 애플리케이션 로그 (INFO 레벨 이상)
├── twinstar-error.log         # 에러 로그만 별도 저장
├── twinstar-sql.log           # SQL 쿼리 로그 (p6spy)
└── twinstar-api.log           # API 요청/응답 로그 (AOP)
```

## 로그 파일 수집 정책
- **롤링 정책**: 일별 + 크기별 (10MB)
- **파일명 패턴**: `twinstar-YYYY-MM-DD.n.log`
- **보관 기간**: 30일
- **인코딩**: UTF-8

## 프로파일별 로그 레벨

### local 프로파일
```yaml
com.TwinStar.TwinStar: DEBUG
org.springframework.web: DEBUG
org.hibernate.SQL: DEBUG
p6spy: INFO
```

### prod 프로파일
```yaml
com.TwinStar.TwinStar: INFO
org.springframework.web: WARN
p6spy: INFO
```

### docker 프로파일
```yaml
com.TwinStar.TwinStar: INFO
p6spy: INFO
```

## 로깅 사용 방법

### 1. 기본 로깅
모든 클래스에서 Lombok의 `@Slf4j` 어노테이션을 사용합니다:

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class YourService {
    public void someMethod() {
        log.info("메서드 시작 - 파라미터: {}", param);
        log.debug("디버그 정보: {}", debugInfo);
        log.warn("경고: {}", warningMessage);
        log.error("에러 발생", exception);
    }
}
```

### 2. 로그 레벨 가이드

#### TRACE
- 가장 상세한 정보
- 개발 중 디버깅용
- 운영 환경에서는 사용하지 않음

#### DEBUG
- 상세한 디버그 정보
- 개발 및 로컬 환경에서 사용
- 변수 값, 중간 처리 결과 등

```java
log.debug("사용자 조회 - userId: {}, userName: {}", userId, userName);
```

#### INFO
- 일반적인 정보성 메시지
- 주요 비즈니스 로직의 시작/종료
- 운영 환경에서도 활성화

```java
log.info("[FollowService] toggleFollow started - receiveUserId: {}", receiveUserId);
log.info("[PostService] 게시물 생성 완료 - postId: {}", postId);
```

#### WARN
- 잠재적인 문제 상황
- 에러는 아니지만 주의가 필요한 상황
- 비즈니스 로직 예외

```java
log.warn("[Exception] DuplicateEmailException: {}", ex.getMessage());
log.warn("유효하지 않은 요청 - userId: {}", userId);
```

#### ERROR
- 에러 상황
- 예외 스택 트레이스 포함
- 즉각적인 조치가 필요한 상황

```java
log.error("[Exception] Unexpected error occurred", exception);
log.error("데이터베이스 연결 실패", ex);
```

### 3. 로깅 패턴 예제

#### Service Layer
```java
@Slf4j
@Service
public class UserService {

    @Transactional
    public User createUser(UserCreateDto dto) {
        log.info("[UserService] 사용자 생성 시작 - email: {}", dto.getEmail());

        try {
            // 비즈니스 로직
            User user = userRepository.save(newUser);
            log.info("[UserService] 사용자 생성 완료 - userId: {}", user.getId());
            return user;
        } catch (Exception e) {
            log.error("[UserService] 사용자 생성 실패 - email: {}", dto.getEmail(), e);
            throw e;
        }
    }

    public User getUser(Long userId) {
        log.debug("[UserService] 사용자 조회 - userId: {}", userId);

        return userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("[UserService] 사용자를 찾을 수 없음 - userId: {}", userId);
                return new EntityNotFoundException("User not found");
            });
    }
}
```

#### Controller Layer
컨트롤러는 AOP와 LoggingFilter에서 자동으로 로깅됩니다.
추가 로깅이 필요한 경우에만 명시적으로 추가하세요:

```java
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserCreateDto dto) {
        // AOP에서 자동으로 로깅되므로 추가 로깅 불필요
        User user = userService.createUser(dto);
        return ResponseEntity.ok(UserDto.from(user));
    }
}
```

#### Repository Layer
Repository는 p6spy와 Hibernate SQL 로깅으로 자동 처리됩니다.
추가 로깅이 필요한 경우:

```java
@Slf4j
public interface UserRepository extends JpaRepository<User, Long> {

    default User findByEmailOrThrow(String email) {
        log.debug("[UserRepository] 이메일로 사용자 조회 - email: {}", email);
        return findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
```

## 자동 로깅 시스템

### 1. AOP 기반 성능 로깅 (TimeTraceAop)
모든 Service, RestController, Controller의 메서드 실행 시간을 자동으로 로깅합니다.

**로그 예시:**
```
2025-01-14 23:30:15.123 [http-nio-8080-exec-1] INFO  c.T.T.aop.TimeTraceAop - [AOP] FollowService.toggleFollow | 45 ms
```

### 2. HTTP 요청/응답 로깅 (LoggingFilter)
모든 HTTP 요청과 응답을 자동으로 로깅합니다.

**로그 예시:**
```
2025-01-14 23:30:15.078 [http-nio-8080-exec-1] INFO  c.T.T.c.f.LoggingFilter - [Request] POST /api/follow/123 | Duration: 45ms | Headers: {content-type=application/json, ...}
2025-01-14 23:30:15.123 [http-nio-8080-exec-1] INFO  c.T.T.c.f.LoggingFilter - [Response] Status: 200 | Duration: 45ms
```

### 3. 예외 로깅 (GlobalExceptionHandler)
모든 예외를 중앙에서 처리하고 로깅합니다.

**로그 예시:**
```
2025-01-14 23:30:15.123 [http-nio-8080-exec-1] WARN  c.T.T.c.e.GlobalExceptionHandler - [Exception] DuplicateEmailException: 이미 존재하는 이메일입니다.
```

### 4. SQL 쿼리 로깅 (p6spy)
모든 SQL 쿼리와 실행 시간을 자동으로 로깅합니다.

**로그 예시:**
```
2025-01-14 23:30:15.100 - SELECT * FROM users WHERE id = 1 | 5 ms
```

## 로깅 모범 사례

### DO
- 파라미터와 함께 로그 메시지 작성
- 명확하고 일관된 로그 포맷 사용 `[클래스명] 액션 - 상세정보`
- 비즈니스 로직의 중요한 시작/종료 지점에 로깅
- 예외 발생 시 스택 트레이스 포함
- 민감한 정보는 마스킹 처리

```java
// Good
log.info("[UserService] 로그인 성공 - userId: {}", userId);
log.error("[OrderService] 결제 처리 실패 - orderId: {}, 사유: {}", orderId, reason, exception);
```

### DON'T
- 민감한 정보(비밀번호, 토큰, 카드번호 등)를 로그에 남기지 않기
- 반복문 안에서 과도한 로깅 피하기
- 불필요한 String 연결 연산 피하기 (파라미터 바인딩 사용)

```java
// Bad
log.info("User login: " + userId);  // String 연결 사용하지 말 것
log.info("Password: {}", password);  // 민감 정보 로깅 금지

// Good
log.info("User login: {}", userId);  // 파라미터 바인딩 사용
log.info("Login attempt for userId: {}", userId);  // 민감 정보 제외
```

## 로그 모니터링 및 분석

### 로그 파일 확인
```bash
# 실시간 로그 확인
tail -f logs/twinstar.log

# 에러 로그만 확인
tail -f logs/twinstar-error.log

# 특정 키워드 검색
grep "Exception" logs/twinstar.log

# 특정 날짜 로그 확인
cat logs/twinstar-2025-01-14.0.log
```

### Docker 환경에서 로그 확인
```bash
# 컨테이너 로그 확인
docker logs twinstar-app

# 실시간 로그 확인
docker logs -f twinstar-app

# 최근 100줄 확인
docker logs --tail 100 twinstar-app
```

## 문제 해결

### 로그가 파일에 기록되지 않는 경우
1. `logs` 디렉토리 권한 확인
2. `logback-spring.xml` 설정 확인
3. 애플리케이션 재시작

### 로그 파일이 너무 큰 경우
- logback-spring.xml에서 `maxFileSize`, `maxHistory` 조정
- 불필요한 DEBUG 레벨 로깅 비활성화

### 성능 저하
- 로그 레벨을 INFO 이상으로 조정
- 반복문 내부의 로깅 최소화
- 비동기 로깅 고려 (필요시)

## 추가 설정

### 특정 패키지의 로그 레벨 변경
`application.yml` 또는 `application-{profile}.yml`에서 설정:

```yaml
logging:
  level:
    com.TwinStar.TwinStar.user: DEBUG
    com.TwinStar.TwinStar.post: INFO
    org.springframework.security: WARN
```

### 로그 패턴 커스터마이징
`logback-spring.xml`에서 pattern 수정:

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
```

## 참고 자료
- [Logback 공식 문서](http://logback.qos.ch/documentation.html)
- [SLF4J 공식 문서](http://www.slf4j.org/manual.html)
- [P6Spy 공식 문서](https://p6spy.readthedocs.io/)
