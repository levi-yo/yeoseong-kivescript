package com.korea.kivescript.session.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.TableGenerator;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자 세션정보 엔티티
 * @author yun-yeoseong
 *
 */
@Entity
@Table(name = "KIVE_USER_SESSION")
@Setter
@Getter
public class UserSession {
	
	@Id
	@Column(name = "USER_SESSION_ID")
	@TableGenerator(
			name="USER_SESSION_SEQ_GENERATOR",
			table="TB_SEQUENCE",
			pkColumnName="SEQ_NAME",
			pkColumnValue="USER_SESSION_SEQ",
			allocationSize=1
	)
	@GeneratedValue(strategy=GenerationType.TABLE, generator = "USER_SESSION_SEQ_GENERATOR")
	private long id ;
	
	private String username;
}
