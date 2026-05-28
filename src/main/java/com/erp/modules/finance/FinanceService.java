package com.erp.modules.finance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 财务模块公开的服务 — 可被其他模块调用。
 */
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    /** 创建会计凭证 */
    public String createJournalEntry(String accountCode, BigDecimal amount,
                                      String direction, String description) {
        log.info("Creating journal entry: {} {} {} — {}", accountCode, direction, amount, description);
        return "JE-" + System.currentTimeMillis();
    }

    /** 查询科目余额 */
    public Map<String, Object> getAccountBalance(String accountCode, String period) {
        log.info("Querying balance for account {} period {}", accountCode, period);
        return Map.of(
            "accountCode", accountCode,
            "period", period,
            "debitTotal", "50000.00",
            "creditTotal", "35000.00",
            "balance", "15000.00"
        );
    }

    /** 检查发票是否存在 */
    public boolean invoiceExists(String invoiceNo) {
        return false;
    }
}
