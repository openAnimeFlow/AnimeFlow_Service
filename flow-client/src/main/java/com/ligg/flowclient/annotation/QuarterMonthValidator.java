package com.ligg.flowclient.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class QuarterMonthValidator implements ConstraintValidator<QuarterMonth, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || value == 1 || value == 4 || value == 7 || value == 10;
    }
}
