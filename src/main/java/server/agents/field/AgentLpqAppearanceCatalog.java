package server.agents.field;

import provider.wz.WZFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LPQ fixture appearances drawn from every gender-compatible WZ face and hair entry. */
final class AgentLpqAppearanceCatalog {
    private static final Pattern WZ_ENTRY = Pattern.compile("^(\\d+)\\.img\\.xml$");
    private static final List<Integer> MALE_FACES = load("Face", 20);
    private static final List<Integer> FEMALE_FACES = load("Face", 21);
    private static final List<Integer> MALE_HAIR = load("Hair", 30, 33);
    private static final List<Integer> FEMALE_HAIR = load("Hair", 31, 34);

    private AgentLpqAppearanceCatalog() { }

    static Appearance select(int gender, long seed) {
        List<Integer> faces = faces(gender);
        List<Integer> hair = hair(gender);
        SplittableRandom random = new SplittableRandom(seed);
        return new Appearance(faces.get(random.nextInt(faces.size())),
                hair.get(random.nextInt(hair.size())));
    }

    static List<Integer> faces(int gender) {
        return gender == 0 ? MALE_FACES : FEMALE_FACES;
    }

    static List<Integer> hair(int gender) {
        return gender == 0 ? MALE_HAIR : FEMALE_HAIR;
    }

    private static List<Integer> load(String directory, int... genderBuckets) {
        Path root = WZFiles.CHARACTER.getFile().resolve(directory);
        try (var files = Files.list(root)) {
            List<Integer> ids = files
                    .map(path -> parseId(path.getFileName().toString()))
                    .filter(id -> id > 0 && contains(genderBuckets, id / 1_000))
                    .sorted()
                    .toList();
            if (ids.isEmpty()) {
                throw new IllegalStateException("No LPQ " + directory + " appearances found in " + root);
            }
            return ids;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load LPQ appearances from " + root, error);
        }
    }

    private static int parseId(String filename) {
        Matcher matcher = WZ_ENTRY.matcher(filename);
        if (!matcher.matches()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean contains(int[] values, int candidate) {
        for (int value : values) if (value == candidate) return true;
        return false;
    }

    record Appearance(int faceId, int hairId) { }
}
