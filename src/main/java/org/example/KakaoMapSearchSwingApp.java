package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class KakaoMapSearchSwingApp extends JFrame {
    private final JTextField keywordField = new JTextField();
    private final JTextArea resultArea = new JTextArea();
    private final JButton searchButton = new JButton("검색");
    private final JButton previousPageButton = new JButton("이전 페이지");
    private final JButton nextPageButton = new JButton("다음 페이지");
    private final JButton saveButton = new JButton("엑셀로 저장");

    private int page = 1;
    private int maxPage = 0;
    private boolean isEnd = false;
    private final List<String[]> allResults = new ArrayList<>();
    private String currentKeyword = "";
    private final int slice = 15;

    private static final Dotenv dotenv = Dotenv.load();
    private static final String API_KEY = dotenv.get("KakaoMapApiKey");
    private static final String EpostUrlWithApiKey = dotenv.get("EpostUrlWithApiKey");
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public KakaoMapSearchSwingApp() {
        setTitle("카카오맵 장소 검색기");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        resultArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(resultArea);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JLabel("검색어: "), BorderLayout.WEST);
        topPanel.add(keywordField, BorderLayout.CENTER);
        topPanel.add(searchButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(previousPageButton);
        bottomPanel.add(nextPageButton);
        bottomPanel.add(saveButton);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        searchButton.addActionListener(this::onSearch);
        keywordField.addActionListener(this::onSearch);
        previousPageButton.addActionListener(e -> previousSearch(page - 1));
        nextPageButton.addActionListener(e -> search(page + 1));
        saveButton.addActionListener(e -> saveToExcel());
    }

    private void onSearch(ActionEvent e) {
        search(1);
    }

    private void initVar(){
        isEnd = false;
        page = 1;
        maxPage = 0;
        allResults.clear();
        currentKeyword = "";
    }

    private void search(int targetPage) {
        String inputKeyword = keywordField.getText().trim();

        //저장된 키워드가 있으면 가져오기. 없으면 입력된거 가져오기
        String keyword = currentKeyword.isEmpty() ? inputKeyword : currentKeyword;

        //저장된 키워드랑 입력된 키워드가 다르면 초기화, 이전 데이터 삭제
        if(!inputKeyword.equals(keyword)){
            initVar();
            targetPage = 1;
            keyword = inputKeyword;
        }

        if (isEnd && targetPage > maxPage) {
            show("마지막 페이지 입니다.");
            return;
        }

        if(targetPage <= maxPage){
            previousSearch(targetPage);
            return;
        }

        if (keyword.isEmpty()) {
            show("검색어를 입력하세요.");
            return;
        }

        resultArea.setText("");

        if (targetPage == 1) {
            currentKeyword = keyword;
        }

        HttpResponse<String> response = null;
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String kakaoUrl = "https://dapi.kakao.com/v2/local/search/keyword.json?query=" + encoded + "&page=" + targetPage;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(kakaoUrl))
                    .header("Authorization", "KakaoAK " + API_KEY)
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            LogUtil.logError("카카오맵 검색중 오류발생: " , e);
        }

        try {
            assert response != null;
            JsonNode root = mapper.readTree(response.body());

            JsonNode meta = root.get("meta");
            isEnd = meta.get("is_end").asText().equals("true");

            resultArea.append("page: " + targetPage + "\ntotal_count: " + meta.get("total_count").asText());
            resultArea.append("\nis_end: " + meta.get("is_end").asText() + "\n");

            JsonNode docs = root.get("documents");

            for (JsonNode doc : docs) {
                String name = doc.get("place_name").asText();
                String addr = doc.get("address_name").asText();
                String road = doc.get("road_address_name").asText();
                String zipNo = road.isEmpty() ? searchZipNo(addr) : searchZipNo(road);

                resultArea.append("[" + name + "]\n지번: " + addr + "\n도로명: " + road + "\n우편번호: " + zipNo + "\n\n");

                if (targetPage > maxPage) {
                    allResults.add(new String[]{name, zipNo, addr, road});
                }
            }

            maxPage = Math.max(maxPage, targetPage);
            page = targetPage;

        } catch (Exception e) {
            LogUtil.logError("카카오맵 검색 결과 파싱중 오류발생: " , e);
        }
    }

    private void previousSearch(int targetPage){
        if (targetPage == 0) {
            show("첫번째 페이지 입니다.");
            return;
        }

        if(allResults.isEmpty()){
            return;
        }

        resultArea.setText("");
        for (int i = (targetPage - 1) * slice; i < targetPage * slice; i++) {
            String[] result = allResults.get(i);
            String name = result[0];
            String zipNo = result[1];
            String addr = result[2];
            String road = result[3];

            resultArea.append("[" + name + "]\n지번: " + addr + "\n도로명: " + road + "\n우편번호: " + zipNo + "\n\n");
        }

        page = targetPage;
    }

    private String searchZipNo(String roadAddress) {
        HttpResponse<String> response;

        try {
            String encoded = URLEncoder.encode(roadAddress, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EpostUrlWithApiKey + "&srchwrd=" + encoded))
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e){
            LogUtil.logError("우편번호 검색중 오류발생: " , e);
            return "";
        }

        try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)));

                NodeList list = doc.getElementsByTagName("zipNo");
                return list.item(0).getTextContent();
        } catch (Exception e) {
            LogUtil.logError("우편번호 검색 결과 파싱 중 오류발생: " , e);
            return "";
        }
    }
    //TODO: allResult를 해쉬맵으로 바꿔서 순서에 자유롭게 만들기
    private void saveToExcel() {
        if (allResults.isEmpty()) {
            show("저장할 데이터가 없습니다.");
            return;
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("검색결과");

            int rowNum = 0;
            Row header = sheet.createRow(rowNum++);
            header.createCell(0).setCellValue("이름");
            header.createCell(1).setCellValue("우편번호");
            header.createCell(2).setCellValue("지번주소");
            header.createCell(3).setCellValue("도로명주소");

            for (String[] row : allResults) {
                Row r = sheet.createRow(rowNum++);
                for (int i = 0; i < row.length; i++) {
                    r.createCell(i).setCellValue(row[i]);
                }
            }

            try (FileOutputStream out = new FileOutputStream("검색결과.xlsx")) {
                workbook.write(out);
            }

            show("엑셀 파일로 저장되었습니다.");
        } catch (Exception e) {
            LogUtil.logError("오류발생: " , e);
            show("엑셀 저장 중 오류: " + e.getMessage());
        }
    }

    private void show(String message) {
        resultArea.append(message + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new KakaoMapSearchSwingApp().setVisible(true));
    }
}

