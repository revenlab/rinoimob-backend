package com.rinoimob.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "A senha deve ter no mínimo 6 caracteres e conter letras maiúsculas, minúsculas, números e um caractere especial";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
