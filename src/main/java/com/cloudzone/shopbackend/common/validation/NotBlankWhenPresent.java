package com.cloudzone.shopbackend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects a blank string <em>only when the field was actually supplied</em>.
 *
 * <p>PATCH bodies in this codebase treat {@code null} as "leave as is" (see
 * {@code ShippingMethodUpdateRequest}). Plain {@code @NotBlank} cannot express that: it
 * rejects {@code null} too, which would make every partial update fail. Conversely, a
 * bare {@code @Size} accepts {@code ""}, letting a client blank out a column the schema
 * declares {@code NOT NULL} — the write would only fail later, deep in Hibernate.
 *
 * <p>This constraint covers the middle ground: {@code null} passes (no change),
 * {@code "   "} fails (a supplied value that is empty after trimming).
 */
@Documented
@Constraint(validatedBy = NotBlankWhenPresent.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotBlankWhenPresent {

    String message() default "must not be blank when supplied";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** {@code null} is valid here; only whitespace-only text is rejected. */
    class Validator implements ConstraintValidator<NotBlankWhenPresent, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || !value.isBlank();
        }
    }
}
