package com.example.todo.exception;

import com.example.todo.domain.entity.enums.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    /* Common Error */
    INVALID_INPUT_VALUE(BAD_REQUEST, " 잘못된 입력값입니다."),
    NOT_MATCH_IAMPORT_AMOUNT(BAD_REQUEST, "실제 결제금액과 서버의 결제금액이 다릅니다."),
    NOT_MATCH_AMOUNT(BAD_REQUEST, "실제 결제금액과 DB의 결제금액이 다릅니다."),
    INVALID_PAYMENT_STATUS(BAD_REQUEST, "유효하지 않은 결제 상태입니다."),
    NOT_MATCH_IAMPORT_CANCEL_AMOUNT(BAD_REQUEST, "환불 요청 금액과 서버의 결제금액이 다릅니다."),
    ALREADY_CANCELED(BAD_REQUEST, "이미 환불된 결제입니다."),
    EXCEED_ALLOWED_TEAM_MEMBERS(BAD_REQUEST, "허용된 최대인원을 초과했습니다."),
    ALREADY_ACTIVE_USERS_SUBSCRIPTION(BAD_REQUEST, "이미 구독중인 구독권이 있습니다."),
    NOT_ALLOWED_MESSAGE(BAD_REQUEST,"본인에게 메시지를 남길 수 없습니다."),
    NOT_AVAILABLE_FUNCTION(BAD_REQUEST, "기능을 사용할 수 없습니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에서 오류가 발생했습니다."),

    NOT_MATCH_USERID(NOT_FOUND, "권한이 없습니다."),
    NOT_MATCH_MANAGERID(NOT_FOUND, "팀매니저가 아닙니다."),
    NOT_MATCH_MEMBERID(NOT_FOUND, "팀원이 아닙니다."),
    NOT_MATCH_TEAM_AND_TASK(NOT_FOUND, "해당팀의 업무가 아닙니다."),
    NOT_MATCH_USERS_AND_USERS_SUBSCRIPTION(NOT_FOUND, "해당 유저의 구독권이 아닙니다."),

    NOT_FOUND_ENTITY(NOT_FOUND, "데이터가 존재하지 않습니다."),
    NOT_FOUND_TEAM(NOT_FOUND, "해당팀이 존재하지 않습니다."),
    NOT_FOUND_TODO(NOT_FOUND, "해당TODO가 존재하지 않습니다."),
    NOT_FOUND_USER(NOT_FOUND, "해당USER가 존재하지 않습니다."),
    NOT_FOUND_COMMENT(NOT_FOUND, "해당Comment가 존재하지 않습니다."),

    NOT_FOUND_TASK(NOT_FOUND, "해당업무가 존재하지 않습니다."),
    NOT_FOUND_MEMBER(NOT_FOUND, "해당 팀에 가입된 상태가 아닙니다."),

    NOT_FOUND_SUBSCRIPTION(NOT_FOUND, "해당 구독권이 존재하지 않습니다."),
    NOT_FOUND_USERS_SUBSCRIPTION(NOT_FOUND, "해당 구독 내역이 존재하지 않습니다."),
    NOT_FOUND_ACTIVE_SUBSCRIPTION(NOT_FOUND, "해당 활성화된 구독권이 존재하지 않습니다."),
    NOT_FOUND_PAYMENT(NOT_FOUND, "해당 결제 상제 정보가 존재하지 않습니다."),


    ALREADY_USER_USERNAME(CONFLICT, "이미 존재하는 사용자입니다."),
    ALREADY_USER_JOINED(BAD_REQUEST, "이미 해당 팀에 가입한 유저입니다."),
    MISMATCH_MANAGERID_USERID(BAD_REQUEST, "팀의 관리자가 아닙니다.");

    private final HttpStatus httpStatus;
    private final String message;

}