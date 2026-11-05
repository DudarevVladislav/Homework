import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public class Main {

    static class Sale {
        String id;
        String date;
        String drink;
        int volume_ml;
        double price;
        String barista;
        String payment;
        String note;
        boolean isValid;

        public Sale(String[] parts) {
            try {
                this.id = parts[0].replace("\"", "").trim();
                this.date = parts[1].replace("\"", "").trim();
                this.drink = parts[2].replace("\"", "").trim();
                this.volume_ml = Integer.parseInt(parts[3].replace("\"", "").trim());
                this.price = Double.parseDouble(parts[4].replace("\"", "").trim());
                this.barista = parts[5].replace("\"", "").trim();
                this.payment = parts[6].replace("\"", "").trim();
                this.note = parts.length > 7 ? parts[7].replace("\"", "").trim() : "";

                this.isValid = !this.payment.isEmpty() && this.price != 0.00;
            } catch (Exception e) {
                this.isValid = false;
            }
        }
    }

    private static String[] parseCsvLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }

    public static void main(String[] args) {
        String fileSeptember = "data/sales_september.csv";
        String fileOctober = "data/sales_october.csv";
        String fileReport = "report.txt";
        String fileGzip = "report.gz";

        List<Sale> allSales = new ArrayList<>();
        List<Sale> septemberSales = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(fileSeptember), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                Sale sale = new Sale(parseCsvLine(line));
                septemberSales.add(sale);
                allSales.add(sale);
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения сентября: " + e.getMessage());
        }

        try {
            List<String> lines = Files.readAllLines(Paths.get(fileOctober), Charset.forName("Windows-1251"));
            for (int i = 1; i < lines.size(); i++) {
                allSales.add(new Sale(parseCsvLine(lines.get(i))));
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения октября: " + e.getMessage());
        }

        long totalRows = allSales.size();

        long emptyPaymentRows = allSales.stream()
                .filter(s -> s.payment.isEmpty())
                .count();

        long zeroPriceRows = allSales.stream()
                .filter(s -> s.price == 0.00)
                .count();

        Map<String, Double> revenueByDrink = allSales.stream()
                .filter(s -> s.isValid)
                .collect(Collectors.groupingBy(s -> s.drink, Collectors.summingDouble(s -> s.price)));

        String maxRevenueDrink = revenueByDrink.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Нет данных");

        long maxRevenue = Math.round(revenueByDrink.getOrDefault(maxRevenueDrink, 0.0));

        String topBarista = "Нет данных";
        try (Stream<String> streamLines = Files.lines(Paths.get(fileOctober), Charset.forName("Windows-1251"))) {
            topBarista = streamLines
                    .skip(1)
                    .map(Main::parseCsvLine)
                    .map(Sale::new)
                    .filter(s -> s.barista != null && !s.barista.isEmpty())
                    .collect(Collectors.groupingBy(s -> s.barista, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("Нет данных");
        } catch (IOException e) {
            System.err.println("Ошибка Stream API (октябрь): " + e.getMessage());
        }

        long cardInSeptember = septemberSales.stream()
                .filter(s -> s.payment.equalsIgnoreCase("карта"))
                .count();

        String bestDate = allSales.stream()
                .filter(s -> s.isValid)
                .collect(Collectors.groupingBy(s -> s.date, Collectors.summingDouble(s -> s.price)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Нет данных");

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileReport), StandardCharsets.UTF_8)) {
            writer.write(String.valueOf(totalRows)); writer.newLine();
            writer.write(String.valueOf(emptyPaymentRows)); writer.newLine();
            writer.write(String.valueOf(zeroPriceRows)); writer.newLine();
            writer.write(maxRevenueDrink); writer.newLine();
            writer.write(String.valueOf(maxRevenue)); writer.newLine();
            writer.write(topBarista); writer.newLine();
            writer.write(String.valueOf(cardInSeptember)); writer.newLine();
            writer.write(bestDate); writer.newLine();
        } catch (IOException e) {
            System.err.println("Ошибка записи отчета: " + e.getMessage());
        }

        System.out.println("1. Всего строк: " + totalRows);
        System.out.println("2. Пустой payment: " + emptyPaymentRows);
        System.out.println("3. Нулевая цена: " + zeroPriceRows);
        System.out.println("4. Топ напиток: " + maxRevenueDrink);
        System.out.println("5. Выручка топа: " + maxRevenue);
        System.out.println("6. Топ бариста в октябре: " + topBarista);
        System.out.println("7. Карта в сентябре: " + cardInSeptember);
        System.out.println("8. Лучшая дата: " + bestDate);

        runBonusTasks(fileReport, fileGzip, fileSeptember);
    }

    private static void runBonusTasks(String fileReport, String fileGzip, String fileSource) {
        try (InputStream in = new FileInputStream(fileReport);
             OutputStream out = new GZIPOutputStream(new FileOutputStream(fileGzip))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            System.out.println("Файл " + fileGzip + " успешно создан.");
        } catch (IOException e) {
            System.err.println("Ошибка архивации: " + e.getMessage());
        }

        long start = System.nanoTime();
        try (FileInputStream in = new FileInputStream(fileSource);
             FileOutputStream out = new FileOutputStream("copy_byte.txt")) {
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        long timeByte = System.nanoTime() - start;

        start = System.nanoTime();
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileSource));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream("copy_buf.txt"))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        long timeBuf = System.nanoTime() - start;

        System.out.println("Время (побайтово): " + timeByte / 1_000_000.0 + " мс");
        System.out.println("Время (буфером): " + timeBuf / 1_000_000.0 + " мс");
    }
}