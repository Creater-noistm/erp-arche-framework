package com.erp.exception;

/** ERP 业务异常基类 */
public class ErpException extends RuntimeException {
    public ErpException(String message) { super(message); }
    public ErpException(String message, Throwable cause) { super(message, cause); }
}
