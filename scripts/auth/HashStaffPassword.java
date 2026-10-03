import com.ngockhanh.clinic.identity.infrastructure.security.UserPasswordEncoder;
import java.util.Arrays;

/** Run with the compiled application and Maven dependency classpath; never pass passwords as arguments. */
class HashStaffPassword {
    public static void main(String[] args) {
        if (args.length != 0 || System.console() == null) {
            throw new IllegalStateException("Run interactively in a terminal without command-line arguments");
        }
        char[] password = System.console().readPassword("Staff password: ");
        if (password == null) throw new IllegalStateException("Password input cancelled");
        try {
            System.out.println(new UserPasswordEncoder().encode(new String(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
