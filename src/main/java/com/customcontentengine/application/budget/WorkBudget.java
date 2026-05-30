package com.customcontentengine.application.budget;

public final class WorkBudget {
    private final int maxOperations;
    private int usedOperations;

    public WorkBudget(int maxOperations) {
        if (maxOperations < 0) {
            throw new IllegalArgumentException("maxOperations must not be negative");
        }
        this.maxOperations = maxOperations;
    }

    public boolean tryConsume(int operations) {
        if (operations < 0) {
            throw new IllegalArgumentException("operations must not be negative");
        }
        if (usedOperations + operations > maxOperations) {
            return false;
        }
        usedOperations += operations;
        return true;
    }

    public int remainingOperations() {
        return maxOperations - usedOperations;
    }
}
