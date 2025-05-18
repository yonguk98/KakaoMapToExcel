package org.example;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtil {

    public static void logError(String message, Exception e) {
        try {
            // 오늘 날짜로 로그 파일 이름 만들기 (예: logs/error-2025-05-15.log)
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            String fileName = "logs/error-" + today + ".log";

            // 로그 디렉토리가 없으면 생성
            java.io.File logDir = new java.io.File("logs");
            if (!logDir.exists()) logDir.mkdirs();

            // 로그 파일에 덧붙이기
            try (PrintWriter out = new PrintWriter(new FileWriter(fileName, true))) {
                out.println("[" + new Date() + "] " + message);
                if (e != null) {
                    e.printStackTrace(out);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace(); // 로그 기록 자체 실패 시 콘솔 출력
        }
    }
}

