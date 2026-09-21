package com.sea.budgetservice.kafka;

public class MalformedExpenseEventException extends IllegalArgumentException {

    public MalformedExpenseEventException(String message) {
        super(message);
    }

    public MalformedExpenseEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
