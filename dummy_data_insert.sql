-- ======================================
-- 더미 데이터 생성 스크립트
-- 목적: 성능 테스트 및 병목 현상 발견
-- ======================================

-- 1. 사용자 더미 데이터 생성 (10,000명)
DELIMITER $$
CREATE PROCEDURE insert_dummy_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE random_sex VARCHAR(10);
    DECLARE random_visibility VARCHAR(20);

    WHILE i <= 10000 DO
        -- 랜덤 성별 선택
        SET random_sex = ELT(1 + FLOOR(RAND() * 2), 'MALE', 'FEMALE');

        -- 랜덤 가시성 선택
        SET random_visibility = ELT(1 + FLOOR(RAND() * 4), 'ALL', 'FOLLOW', 'ONLYME',"LOCK");

        INSERT INTO user (
            email, password, nick_name, profile_img, profile_txt,
            del_yn, sex, id_visibility, admin_yn, user_status,
            created_time, updated_time
        ) VALUES (
            CONCAT('user', i, '@test.com'),
            '$2a$10$dummypasswordhash123456789012345678901234567890', -- bcrypt 형식의 더미 해시
            CONCAT('TestUser', i),
            '/images/default_profile.png',
            CONCAT('안녕하세요! 테스트 유저 ', i, '입니다.'),
            'N',
            random_sex,
            random_visibility,
            IF(i <= 10, 'ADMIN', 'USER'), -- 처음 10명은 관리자
            'ACTIVE',
            NOW() - INTERVAL FLOOR(RAND() * 365) DAY, -- 최근 1년 내 가입
            NOW() - INTERVAL FLOOR(RAND() * 30) DAY
        );

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

-- 2. 게시물 더미 데이터 생성 (50,000개)
DELIMITER $$
CREATE PROCEDURE insert_dummy_posts()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE random_user_id INT;
    DECLARE random_visibility VARCHAR(20);

    WHILE i <= 50000 DO
        SET random_user_id = 1 + FLOOR(RAND() * 10000);
        SET random_visibility =ELT(1 + FLOOR(RAND() * 4), 'ALL', 'FOLLOW', 'ONLYME',"LOCK");

        INSERT INTO post (
            user_id, content, post_del, visibility, score,
            created_time, updated_time
        ) VALUES (
            random_user_id,
            CONCAT('테스트 게시물 내용입니다. 게시물 번호: ', i, '. ',
                   REPEAT('더미 텍스트 ', FLOOR(1 + RAND() * 20))),
            'N',
            random_visibility,
            FLOOR(RAND() * 1000), -- 0-999 랜덤 점수
            NOW() - INTERVAL FLOOR(RAND() * 180) DAY, -- 최근 6개월
            NOW() - INTERVAL FLOOR(RAND() * 30) DAY
        );

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

-- 3. 게시물 파일 더미 데이터 생성 (100,000개 - POST당 평균 2개)
DELIMITER $$
CREATE PROCEDURE insert_dummy_post_files()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE random_post_id INT;
    DECLARE file_count INT;

    WHILE i <= 100000 DO
        SET random_post_id = 1 + FLOOR(RAND() * 50000);

        INSERT INTO post_file (
            post_id, file_url, is_active
        ) VALUES (
            random_post_id,
            CONCAT('https://s3.amazonaws.com/twinstar-bucket/images/', UUID(), '.jpg'),
            'Y'
        );

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

-- 4. 게시물 좋아요 더미 데이터 생성 (200,000개)
-- ALARM 병목 테스트를 위한 대량 좋아요 데이터
DELIMITER $$
CREATE PROCEDURE insert_dummy_post_likes()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE random_post_id INT;
    DECLARE random_user_id INT;
    DECLARE duplicate_check INT;

    WHILE i <= 200000 DO
        SET random_post_id = 1 + FLOOR(RAND() * 50000);
        SET random_user_id = 1 + FLOOR(RAND() * 10000);

        -- 중복 체크 (같은 유저가 같은 게시물에 좋아요 방지)
        SELECT COUNT(*) INTO duplicate_check
        FROM post_like
        WHERE post_id = random_post_id AND user_id = random_user_id;

        IF duplicate_check = 0 THEN
            INSERT INTO post_like (post_id, user_id)
            VALUES (random_post_id, random_user_id);
            SET i = i + 1;
        END IF;
    END WHILE;
END$$
DELIMITER ;

-- 5. 알림 더미 데이터 생성 (500,000개 - ALARM 병목 테스트용)
-- REDIS 미활용으로 인한 DB 부하 테스트
DELIMITER $$
CREATE PROCEDURE insert_dummy_alarms()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE random_user_id INT;
    DECLARE random_sender_id INT;

    WHILE i <= 500000 DO
        SET random_user_id = 1 + FLOOR(RAND() * 10000);
        SET random_sender_id = 1 + FLOOR(RAND() * 10000);

        -- 자기 자신에게 알림 방지
        IF random_user_id != random_sender_id THEN
            INSERT INTO alarm (
                user_id, sender_id, url, content, is_read,
                created_time, updated_time
            ) VALUES (
                random_user_id,
                random_sender_id,
                CONCAT('/post/', FLOOR(1 + RAND() * 50000)),
                ELT(1 + FLOOR(RAND() * 5),
                    '회원님의 게시물을 좋아합니다.',
                    '회원님의 게시물에 댓글을 남겼습니다.',
                    '회원님을 팔로우하기 시작했습니다.',
                    '회원님을 태그했습니다.',
                    '회원님의 댓글을 좋아합니다.'
                ),
                IF(RAND() < 0.3, TRUE, FALSE), -- 30%는 읽음 처리
                NOW() - INTERVAL FLOOR(RAND() * 90) DAY, -- 최근 3개월
                NOW() - INTERVAL FLOOR(RAND() * 30) DAY
            );

            SET i = i + 1;
        END IF;
    END WHILE;
END$$
DELIMITER ;

-- 6. 신고 더미 데이터 생성 (10,000개 - REPORT->BAN 악용 가능성 테스트)
DELIMITER $$
CREATE PROCEDURE insert_dummy_reports()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE random_reporter_id INT;
    DECLARE random_reported_id INT;
    DECLARE random_type VARCHAR(20);
    DECLARE random_type_id INT;
    DECLARE random_status VARCHAR(20);

    WHILE i <= 10000 DO
        SET random_reporter_id = 1 + FLOOR(RAND() * 10000);
        SET random_reported_id = 1 + FLOOR(RAND() * 10000);
        SET random_type = ELT(1 + FLOOR(RAND() * 4), 'USER', 'POST', 'COMMENT', 'CHAT');
        -- PENDING 또는 COMPLETE 상태만 존재
        SET random_status = ELT(1 + FLOOR(RAND() * 2), 'PENDING', 'COMPLETE');

        -- type에 따라 적절한 ID 설정
        CASE random_type
            WHEN 'USER' THEN SET random_type_id = 1 + FLOOR(RAND() * 10000);
            WHEN 'POST' THEN SET random_type_id = 1 + FLOOR(RAND() * 50000);
            WHEN 'COMMENT' THEN SET random_type_id = 1 + FLOOR(RAND() * 10000);
            WHEN 'CHAT' THEN SET random_type_id = 1 + FLOOR(RAND() * 10000);
        END CASE;

        -- 자기 자신 신고 방지
        IF random_reporter_id != random_reported_id THEN
            INSERT INTO report (
                reporter_id, reported_id, content, report_type, type_id,
                reported_time, processed_at, comment, report_status, is_deleted
            ) VALUES (
                random_reporter_id,
                random_reported_id,
                ELT(1 + FLOOR(RAND() * 6),
                    '부적절한 콘텐츠',
                    '스팸 및 광고',
                    '욕설 및 비방',
                    '개인정보 노출',
                    '저작권 침해',
                    '기타 규정 위반'
                ),
                random_type,
                random_type_id,
                NOW() - INTERVAL FLOOR(RAND() * 60) DAY,
                IF(random_status = 'COMPLETE',
                   NOW() - INTERVAL FLOOR(RAND() * 30) DAY,
                   NULL),
                IF(random_status = 'COMPLETE',
                   ELT(1 + FLOOR(RAND() * 3),
                       '검토 완료되었습니다.',
                       '규정 위반으로 확인되어 조치되었습니다.',
                       '신고 내용이 규정 위반에 해당하지 않습니다.'
                   ),
                   NULL),
                random_status,
                IF(random_status = 'COMPLETE' AND RAND() < 0.3, TRUE, FALSE) -- 완료된 건 중 30% 삭제 처리
            );

            SET i = i + 1;
        END IF;
    END WHILE;
END$$
DELIMITER ;

-- ======================================
-- 프로시저 실행 (순서 중요!)
-- ======================================

-- 실행 전 기존 프로시저 삭제 (이미 존재하는 경우)
DROP PROCEDURE IF EXISTS insert_dummy_users;
DROP PROCEDURE IF EXISTS insert_dummy_posts;
DROP PROCEDURE IF EXISTS insert_dummy_post_files;
DROP PROCEDURE IF EXISTS insert_dummy_post_likes;
DROP PROCEDURE IF EXISTS insert_dummy_alarms;
DROP PROCEDURE IF EXISTS insert_dummy_reports;

-- 프로시저 재생성 (위의 CREATE PROCEDURE 문들 실행)

-- 순차적 실행 (약 5-10분 소요 예상)
CALL insert_dummy_users();      -- 1. 사용자 10,000명
SELECT '사용자 데이터 삽입 완료' AS status;

CALL insert_dummy_posts();      -- 2. 게시물 50,000개
SELECT '게시물 데이터 삽입 완료' AS status;

CALL insert_dummy_post_files(); -- 3. 게시물 파일 100,000개
SELECT '게시물 파일 데이터 삽입 완료' AS status;

CALL insert_dummy_post_likes(); -- 4. 좋아요 200,000개
SELECT '좋아요 데이터 삽입 완료' AS status;

CALL insert_dummy_alarms();     -- 5. 알림 500,000개 (병목 테스트용)
SELECT '알림 데이터 삽입 완료' AS status;

CALL insert_dummy_reports();    -- 6. 신고 10,000개
SELECT '신고 데이터 삽입 완료' AS status;

-- ======================================
-- 데이터 확인 쿼리
-- ======================================
SELECT
    '사용자' AS 테이블, COUNT(*) AS 레코드수 FROM user
UNION ALL
SELECT
    '게시물' AS 테이블, COUNT(*) AS 레코드수 FROM post
UNION ALL
SELECT
    '게시물_파일' AS 테이블, COUNT(*) AS 레코드수 FROM post_file
UNION ALL
SELECT
    '좋아요' AS 테이블, COUNT(*) AS 레코드수 FROM post_like
UNION ALL
SELECT
    '알림' AS 테이블, COUNT(*) AS 레코드수 FROM alarm
UNION ALL
SELECT
    '신고' AS 테이블, COUNT(*) AS 레코드수 FROM report;

-- ======================================
-- 성능 테스트용 쿼리 예제
-- ======================================

-- 1. ALARM 병목 테스트 - 특정 사용자의 알림 조회 (REDIS 미활용 시 느림)
-- SELECT * FROM alarm WHERE user_id = 1000 AND is_read = FALSE ORDER BY created_time DESC LIMIT 20;

-- 2. POST 조회 성능 테스트 - 페이징 처리
-- SELECT * FROM post WHERE post_del = 'N' ORDER BY created_time DESC LIMIT 20 OFFSET 1000;

-- 3. REPORT 악용 테스트 - 특정 사용자에 대한 신고 건수 (악의적 신고 패턴 감지)
-- SELECT reported_id, COUNT(*) as report_count
-- FROM report
-- WHERE report_status = 'PENDING'
-- GROUP BY reported_id
-- HAVING report_count > 5
-- ORDER BY report_count DESC;

-- 4. POST_FILE S3 연결 문제 테스트 - 대용량 파일 조회
-- SELECT p.id, p.content, GROUP_CONCAT(pf.file_url) as files
-- FROM post p
-- LEFT JOIN post_file pf ON p.id = pf.post_id
-- WHERE p.post_del = 'N'
-- GROUP BY p.id
-- LIMIT 100;

-- ======================================
-- 프로시저 정리 (선택사항)
-- ======================================
-- DROP PROCEDURE IF EXISTS insert_dummy_users;
-- DROP PROCEDURE IF EXISTS insert_dummy_posts;
-- DROP PROCEDURE IF EXISTS insert_dummy_post_files;
-- DROP PROCEDURE IF EXISTS insert_dummy_post_likes;
-- DROP PROCEDURE IF EXISTS insert_dummy_alarms;
-- DROP PROCEDURE IF EXISTS insert_dummy_reports;
