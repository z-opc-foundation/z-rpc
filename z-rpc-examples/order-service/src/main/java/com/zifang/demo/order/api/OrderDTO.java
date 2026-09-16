package com.zifang.demo.order.api;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单 DTO
 */
public class OrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String userName;
    private BigDecimal amount;
    private String status;

    public OrderDTO() {}

    public OrderDTO(Long id, Long userId, String userName, BigDecimal amount, String status) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "OrderDTO{id=" + id + ", userId=" + userId + ", userName='" + userName +
                "', amount=" + amount + ", status='" + status + "'}";
    }
}
