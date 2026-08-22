package server.agents.social.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericDialogueIntentClassifierTest {
    private final GenericDialogueIntentClassifier classifier = new GenericDialogueIntentClassifier();

    @Test
    void classifiesGenericChatWithoutModelDependency() {
        assertEquals("casual.greeting", classifier.classify("yo"));
        assertEquals("casual.status", classifier.classify("how r u today?"));
        assertEquals("casual.thanks", classifier.classify("ty for that"));
        assertEquals("casual.goodbye", classifier.classify("cya later"));
        assertEquals("casual.question", classifier.classify("where are you going?"));
        assertEquals("casual.general", classifier.classify("that was wild"));
    }
}
