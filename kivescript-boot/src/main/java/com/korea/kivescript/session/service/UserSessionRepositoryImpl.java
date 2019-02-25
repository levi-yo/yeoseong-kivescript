package com.korea.kivescript.session.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.korea.kivescript.session.repository.UserSessionRepository;

@Service
public class UserSessionRepositoryImpl implements UserSessionService {
	
	@Autowired
	private UserSessionRepository repository;
}
