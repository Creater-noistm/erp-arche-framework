package com.erp.exception;

/** 数据访问异常（数据库操作失败） */
public class DataAccessException extends ErpException {
    public DataAccessException(String message) { super(message); }
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
