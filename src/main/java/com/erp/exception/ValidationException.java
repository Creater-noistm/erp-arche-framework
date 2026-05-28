package com.erp.exception;

/** 校验异常（必填字段为空、格式错误等） */
public class ValidationException extends ErpException {
    public ValidationException(String message) { super(message); }
}
