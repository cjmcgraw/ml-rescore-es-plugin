package carlm.esplugin;

import static org.junit.jupiter.api.Assertions.*;

import carlm.esplugin.MLRescoreMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class MLRescoreModeTests {
    Random r = new Random(1L);

    @Test
    public void testMlRescoreSumWorks() {
        List<Integer> firstRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());
        List<Integer> secondRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());

        for (int i = 0; i < firstRandoms.size(); i++) {
            assertEquals(
                    firstRandoms.get(i) + secondRandoms.get(i),
                    MLRescoreMode.Sum.combine(firstRandoms.get(i), secondRandoms.get(i))
            );
        }
    }

    @Test
    public void testMlRescoreAvgWorks() {
        List<Integer> firstRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());
        List<Integer> secondRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());

        for (int i = 0; i < firstRandoms.size(); i++) {
            assertEquals(
                    (firstRandoms.get(i) + secondRandoms.get(i)) / 2.0,
                    MLRescoreMode.Avg.combine(firstRandoms.get(i), secondRandoms.get(i))
            );
        }
    }

    @Test
    public void testMlRescoreProductWorks() {
        List<Integer> firstRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());
        List<Integer> secondRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());

        for (int i = 0; i < firstRandoms.size(); i++) {
            assertEquals(
                    firstRandoms.get(i) * secondRandoms.get(i),
                    MLRescoreMode.Product.combine(firstRandoms.get(i), secondRandoms.get(i))
            );
        }
    }

    @Test
    public void testMlRescoreProduct1pWorks() {
        List<Integer> firstRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());
        List<Integer> secondRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());

        for (int i = 0; i < firstRandoms.size(); i++) {
            assertEquals(
                    (firstRandoms.get(i) + 1.0f) * secondRandoms.get(i),
                    MLRescoreMode.Product1p.combine(firstRandoms.get(i), secondRandoms.get(i))
            );
        }
    }

    @Test
    public void testMlRescoreReplaceWorks() {
        List<Integer> firstRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());
        List<Integer> secondRandoms = r.ints(100, -500, 500).boxed().collect(Collectors.toList());

        for (int i = 0; i < firstRandoms.size(); i++) {
            assertEquals(
                    (float) firstRandoms.get(i),
                    MLRescoreMode.Replace.combine(firstRandoms.get(i), secondRandoms.get(i))
            );
        }
    }
}
