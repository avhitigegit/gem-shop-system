package lk.nuvi.gemshop.common.json;

import java.util.ArrayList;
import java.util.List;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.Role;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/** Leaves {@link OwnerOnly} fields out of JSON answers for people who may not see them. */
@Component
public class OwnerOnlyModule extends SimpleModule {

    public OwnerOnlyModule() {
        super("owner-only");
        setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier bean,
                    List<BeanPropertyWriter> props) {
                List<BeanPropertyWriter> out = new ArrayList<>(props.size());
                for (BeanPropertyWriter p : props) {
                    OwnerOnly o = p.getAnnotation(OwnerOnly.class);
                    out.add(o == null ? p : new Hidden(p, o.accountant()));
                }
                return out;
            }
        });
    }

    /** May the signed-in person see owner-only values? */
    public static boolean canSee(boolean accountantToo) {
        Role r = CurrentUser.role().orElse(null);
        return r == Role.OWNER || (accountantToo && r == Role.ACCOUNTANT);
    }

    static final class Hidden extends BeanPropertyWriter {
        private final boolean accountant;

        Hidden(BeanPropertyWriter base, boolean accountant) {
            super(base);
            this.accountant = accountant;
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator g, SerializationContext ctxt) throws Exception {
            if (canSee(accountant)) {
                super.serializeAsProperty(bean, g, ctxt);
            }
        }
    }
}
