package com.ghasl_service.demo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LocationFieldsValidator.class)
@Documented
public @interface ValidLocationFields {
    String message() default "Invalid location fields";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
