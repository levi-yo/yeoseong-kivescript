package com.korea.kivescript.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.korea.kivescript.session.entity.UserSession;

/**
 * UserSession Repository
 * 사용자 세션정보 레포지토리
 * @author yun-yeoseong
 *
 */
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

}
