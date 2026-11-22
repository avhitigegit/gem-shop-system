package lk.nuvi.gemshop.common.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cost price, profit, stock value …: the field is left out of the answer for everyone except the owner
 * (and the accountant when {@code accountant = true}, where the BRD allows it).
 * Use on response record components / fields. Make the field optional in the client types.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface OwnerOnly {
    boolean accountant() default false;
}
