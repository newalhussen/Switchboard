package dev.switchboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.switchboard.Evaluator.Reason;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

    @Test
    void disabledFlagIsAlwaysOff() {
        assertThat(Evaluator.evaluate("f", false, 100, "u1")).isEqualTo(new Evaluator.Result(false, Reason.DISABLED));
    }

    @Test
    void zeroAndHundredPercentDoNotNeedAUser() {
        assertThat(Evaluator.evaluate("f", true, 100, null).value()).isTrue();
        assertThat(Evaluator.evaluate("f", true, 0, "u1").value()).isFalse();
    }

    @Test
    void partialRolloutNeedsAUserId() {
        assertThat(Evaluator.evaluate("f", true, 50, null).reason()).isEqualTo(Reason.USER_REQUIRED);
        assertThat(Evaluator.evaluate("f", true, 50, " ").value()).isFalse();
    }

    @Test
    void assignmentIsDeterministic() {
        for (int i = 0; i < 200; i++) {
            String user = "user-" + i;
            assertThat(Evaluator.evaluate("checkout", true, 37, user)).isEqualTo(Evaluator.evaluate("checkout", true, 37, user));
        }
    }

    @Test
    void rolloutIsMonotonic() {
        // anyone in at 20% must still be in at 60%
        Set<String> at20 = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String u = "u" + i;
            boolean in20 = Evaluator.evaluate("f", true, 20, u).value();
            boolean in60 = Evaluator.evaluate("f", true, 60, u).value();
            if (in20) { at20.add(u); assertThat(in60).isTrue(); }
        }
        assertThat(at20).isNotEmpty();
    }

    @Test
    void distributionMatchesPercentage() {
        int n = 20000, in = 0;
        for (int i = 0; i < n; i++) if (Evaluator.evaluate("new-ui", true, 30, "user-" + i).value()) in++;
        assertThat(in / (double) n).isBetween(0.28, 0.32);
    }

    @Test
    void differentFlagsBucketUsersIndependently() {
        int same = 0, n = 5000;
        for (int i = 0; i < n; i++) {
            String u = "u" + i;
            if (Evaluator.evaluate("a", true, 50, u).value() == Evaluator.evaluate("b", true, 50, u).value()) same++;
        }
        assertThat(same / (double) n).isBetween(0.45, 0.55); // ~50% agreement means no correlation
    }
}
