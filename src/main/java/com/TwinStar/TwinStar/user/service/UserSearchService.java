package com.TwinStar.TwinStar.user.service;

import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.dto.ChatUserListDto;
import com.TwinStar.TwinStar.user.dto.UserDetailDto;
import com.TwinStar.TwinStar.user.dto.UserListDto;
import com.TwinStar.TwinStar.user.dto.UserSearchDto;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 검색 서비스
 * - 채팅용 유저 리스트
 * - 채팅 유저 검색
 * - 관리자용 유저 리스트
 * - 관리자용 유저 상세 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSearchService {

    private final UserRepository userRepository;

    /**
     * 채팅용 유저 리스트
     * @param pageable 페이지 정보
     * @return 유저 리스트 (페이징)
     */
    public Page<ChatUserListDto> chatUserList(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(ChatUserListDto::new);
    }

    /**
     * 채팅 유저 검색 (닉네임 기반)
     * @param nickName 검색할 닉네임 (부분 일치)
     * @param pageable 페이지 정보
     * @return 검색 결과 (페이징)
     */
    public Page<ChatUserListDto> searchChatUsers(String nickName, Pageable pageable) {
        Specification<User> spec = byNickNameContains(nickName);
        Page<User> result = userRepository.findAll(spec, pageable);
        return result.map(ChatUserListDto::new);
    }

    /**
     * 관리자용 유저 검색 (닉네임 기반)
     * @param nickName 검색할 닉네임 (부분 일치)
     * @param pageable 페이지 정보
     * @return 검색 결과 (페이징)
     */
    public Page<UserListDto> searchListUsers(String nickName, Pageable pageable) {
        Specification<User> spec = byNickNameContains(nickName);
        return userRepository.findAll(spec, pageable).map(User::listFromEntity);
    }

    /**
     * 관리자용 유저 리스트
     * @param pageable 페이지 정보
     * @param dto 검색 조건
     * @return 유저 리스트 (페이징)
     */
    public Page<UserListDto> userList(Pageable pageable, UserSearchDto dto) {
        Specification<User> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (dto.getNickName() != null) {
                predicates.add(criteriaBuilder.equal(root.get("nickName"), dto.getNickName()));
            }

            Predicate[] predicateArr = new Predicate[predicates.size()];
            for (int i = 0; i < predicates.size(); i++) {
                predicateArr[i] = predicates.get(i);
            }

            return criteriaBuilder.and(predicateArr);
        };

        return userRepository.findAll(spec, pageable).map(User::listFromEntity);
    }

    /**
     * 관리자용 유저 상세 조회
     * @param userId 조회할 사용자 ID
     * @return 유저 상세 정보
     * @throws EntityNotFoundException 사용자를 찾을 수 없는 경우
     */
    public UserDetailDto userDetailList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("user is not found"));
        return UserDetailDto.detailList(user);
    }

    /**
     * 닉네임 부분 일치 검색 Specification
     * - 대소문자 구분 없이 검색
     * @param nickName 검색할 닉네임
     * @return Specification
     */
    private Specification<User> byNickNameContains(String nickName) {
        return (root, query, criteriaBuilder) -> {
            if (StringUtils.hasText(nickName)) {
                // 참고: LOWER 함수 사용은 DB에 따라 인덱스를 타지 못해 성능 저하를 유발할 수 있음.
                // 대용량 데이터 처리 시 DB에 함수 기반 인덱스(Function-based Index) 생성을 고려해야 함.
                return criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("nickName")),
                        "%" + nickName.toLowerCase() + "%"
                );
            }
            return null;
        };
    }
}
