package com.lugg.mod.client;

import com.lugg.mod.ai.actions.ParsedPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Глобальное состояние запроса к ИИ чтобы можно было свернуть окно и ждать в фоне.
 */
public class AiRequestState {
    public static final AiRequestState INSTANCE = new AiRequestState();

    public boolean requestInProgress = false;
    public boolean finishedSuccessfully = false;
    public boolean failed = false;
    public final List<String> logs = new ArrayList<>();
    public ParsedPlan readyPlan = null;
    public String errorMessage = "";
    public long requestStartTime = 0;
    public String taskType = "";
    public String prompt = "";

    private AiRequestState() {}

    public void reset() {
        requestInProgress = false;
        finishedSuccessfully = false;
        failed = false;
        logs.clear();
        readyPlan = null;
        errorMessage = "";
        requestStartTime = 0;
        taskType = "";
        prompt = "";
    }

    public void log(String line) {
        logs.add(line);
        // Ограничиваем количество логов чтобы не забить память
        if (logs.size() > 100) logs.remove(0);
    }
}
