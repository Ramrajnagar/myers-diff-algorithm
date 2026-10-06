import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Main {

    static class Edit {
        char op; // ' ', '-', '+'
        byte[] line;

        Edit(char op, byte[] line) {
            this.op = op;
            this.line = line;
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: <program> [lines|highlight] <fileA> <fileB>");
            System.exit(2);
        }

        String command = args[0];
        String fileAPath = args[1];
        String fileBPath = args[2];

        if (!command.equals("lines") && !command.equals("highlight")) {
            System.err.println("Invalid command. Use 'lines' or 'highlight'.");
            System.exit(2);
        }

        byte[] bytesA = null;
        byte[] bytesB = null;
        try {
            bytesA = Files.readAllBytes(Paths.get(fileAPath));
            bytesB = Files.readAllBytes(Paths.get(fileBPath));
        } catch (Exception e) {
            System.err.println("Error reading files: " + e.getMessage());
            System.exit(2);
        }

        List<byte[]> linesA = splitLines(bytesA);
        List<byte[]> linesB = splitLines(bytesB);

        List<int[]> trace = buildMyersTrace(linesA, linesB);
        List<Edit> script = backtrack(trace, linesA, linesB);

        if (command.equals("lines")) {
            printLinesDiff(script);
        } else {
            printHighlightDiff(script);
        }
    }

    private static List<byte[]> splitLines(byte[] data) {
        List<byte[]> lines = new ArrayList<>();
        if (data.length == 0) return lines;

        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                byte[] line = Arrays.copyOfRange(data, start, i);
                lines.add(line);
                start = i + 1;
            }
        }
        if (start < data.length) {
            byte[] line = Arrays.copyOfRange(data, start, data.length);
            lines.add(line);
        }
        return lines;
    }

    // ==========================================
    // Myers Diff for Lines (Part A)
    // ==========================================

    private static List<int[]> buildMyersTrace(List<byte[]> A, List<byte[]> B) {
        int n = A.size();
        int m = B.size();
        int maxD = n + m;
        
        // Hashing optimization: drastically speeds up string matching for large files
        int[] hashA = new int[n];
        for (int i = 0; i < n; i++) hashA[i] = Arrays.hashCode(A.get(i));
        int[] hashB = new int[m];
        for (int i = 0; i < m; i++) hashB[i] = Arrays.hashCode(B.get(i));

        int[] v = new int[2 * maxD + 1];
        List<int[]> trace = new ArrayList<>();

        for (int d = 0; d <= maxD; d++) {
            int[] vSlice = new int[2 * d + 1];
            for (int k = -d; k <= d; k += 2) {
                int index = k + maxD;
                int x;
                
                if (d == 0) {
                    x = 0; // Fixes array out-of-bounds when dealing with 0 edits / empty files
                } else {
                    boolean moveDown = (k == -d) || (k != d && v[index + 1] > v[index - 1]);
                    x = moveDown ? v[index + 1] : v[index - 1] + 1;
                }
                int y = x - k;

                // Snake: use precomputed hashes to avoid heavy byte array comparisons when they don't match
                while (x < n && y < m && hashA[x] == hashB[y] && Arrays.equals(A.get(x), B.get(y))) {
                    x++;
                    y++;
                }

                v[index] = x;
                vSlice[k + d] = x;

                if (x >= n && y >= m) {
                    trace.add(vSlice);
                    return trace;
                }
            }
            trace.add(vSlice);
        }
        return trace;
    }

    private static List<Edit> backtrack(List<int[]> trace, List<byte[]> A, List<byte[]> B) {
        int x = A.size();
        int y = B.size();
        List<Edit> script = new ArrayList<>();

        for (int d = trace.size() - 1; d > 0; d--) {
            int[] prevVSlice = trace.get(d - 1);
            int k = x - y;

            boolean moveDown;
            if (k == -d) moveDown = true;
            else if (k == d) moveDown = false;
            else {
                int xFromRight = prevVSlice[(k - 1) + (d - 1)] + 1;
                int xFromDown = prevVSlice[(k + 1) + (d - 1)];
                // Enforces Delete before Insert (pushing insertions lower in the backwards list)
                moveDown = xFromDown >= xFromRight; 
            }

            int prevK = moveDown ? k + 1 : k - 1;
            int prevX = prevVSlice[prevK + (d - 1)];
            int prevY = prevX - prevK;

            int editX = moveDown ? prevX : prevX + 1;
            int editY = moveDown ? prevY + 1 : prevY;

            while (x > editX && y > editY) {
                x--; y--;
                script.add(new Edit(' ', A.get(x)));
            }

            if (moveDown) {
                y--;
                script.add(new Edit('+', B.get(y)));
            } else {
                x--;
                script.add(new Edit('-', A.get(x)));
            }
        }

        while (x > 0 && y > 0) {
            x--; y--;
            script.add(new Edit(' ', A.get(x)));
        }

        Collections.reverse(script);
        return script;
    }

    private static void printEdit(Edit edit) {
        try {
            System.out.write((byte) edit.op);
            System.out.write(edit.line);
            System.out.write(10); // Print exact Unix newline byte
        } catch (IOException e) {
            System.err.println("Error writing to stdout: " + e.getMessage());
        }
    }

    private static void printLinesDiff(List<Edit> script) {
        for (Edit edit : script) {
            printEdit(edit);
        }
        System.out.flush();
    }

    // ==========================================
    // Myers Diff for Characters (Part B)
    // ==========================================

    private static void printHighlightDiff(List<Edit> script) {
        int i = 0;
        while (i < script.size()) {
            if (script.get(i).op == ' ') {
                printEdit(script.get(i));
                i++;
            } else {
                // Change block begins
                List<Edit> blockDeletes = new ArrayList<>();
                int start = i;
                
                while (i < script.size() && script.get(i).op != ' ') {
                    if (script.get(i).op == '-') {
                        blockDeletes.add(script.get(i));
                    }
                    i++;
                }

                int plusCount = 0;
                for (int j = start; j < i; j++) {
                    Edit e = script.get(j);
                    printEdit(e);

                    if (e.op == '+') {
                        if (plusCount < blockDeletes.size()) {
                            printCharacterHighlight(blockDeletes.get(plusCount).line, e.line);
                        }
                        plusCount++;
                    }
                }
            }
        }
        System.out.flush();
    }

    private static void printCharacterHighlight(byte[] oldLine, byte[] newLine) {
        String oldStr = new String(oldLine, StandardCharsets.UTF_8);
        String newStr = new String(newLine, StandardCharsets.UTF_8);

        int[] oldCps = oldStr.codePoints().toArray();
        int[] newCps = newStr.codePoints().toArray();

        List<int[]> trace = buildMyersTrace(oldCps, newCps);
        
        boolean[] oldHighlight = new boolean[oldCps.length];
        boolean[] newHighlight = new boolean[newCps.length];
        calculateHighlights(trace, oldCps, newCps, oldHighlight, newHighlight);

        String oldRanges = formatRanges(oldHighlight);
        String newRanges = formatRanges(newHighlight);
        
        String highlightStr = "? " + oldRanges + " | " + newRanges;
        try {
            System.out.write(highlightStr.getBytes(StandardCharsets.US_ASCII));
            System.out.write(10);
        } catch (IOException e) {
            System.err.println("Error writing to stdout: " + e.getMessage());
        }
    }

    private static List<int[]> buildMyersTrace(int[] A, int[] B) {
        int n = A.length;
        int m = B.length;
        int maxD = n + m;
        int[] v = new int[2 * maxD + 1];
        List<int[]> trace = new ArrayList<>();

        for (int d = 0; d <= maxD; d++) {
            int[] vSlice = new int[2 * d + 1];
            for (int k = -d; k <= d; k += 2) {
                int index = k + maxD;
                int x;
                
                if (d == 0) {
                    x = 0;
                } else {
                    boolean moveDown = (k == -d) || (k != d && v[index + 1] > v[index - 1]);
                    x = moveDown ? v[index + 1] : v[index - 1] + 1;
                }
                int y = x - k;

                while (x < n && y < m && A[x] == B[y]) {
                    x++;
                    y++;
                }

                v[index] = x;
                vSlice[k + d] = x;

                if (x >= n && y >= m) {
                    trace.add(vSlice);
                    return trace;
                }
            }
            trace.add(vSlice);
        }
        return trace;
    }

    private static void calculateHighlights(List<int[]> trace, int[] A, int[] B, boolean[] highlightA, boolean[] highlightB) {
        int x = A.length;
        int y = B.length;

        for (int d = trace.size() - 1; d > 0; d--) {
            int[] prevVSlice = trace.get(d - 1);
            int k = x - y;

            boolean moveDown;
            if (k == -d) moveDown = true;
            else if (k == d) moveDown = false;
            else {
                int xFromRight = prevVSlice[(k - 1) + (d - 1)] + 1;
                int xFromDown = prevVSlice[(k + 1) + (d - 1)];
                moveDown = xFromDown >= xFromRight;
            }

            int prevK = moveDown ? k + 1 : k - 1;
            int prevX = prevVSlice[prevK + (d - 1)];
            int prevY = prevX - prevK;

            int editX = moveDown ? prevX : prevX + 1;
            int editY = moveDown ? prevY + 1 : prevY;

            while (x > editX && y > editY) {
                x--; y--;
            }

            if (moveDown) {
                y--;
                highlightB[y] = true;
            } else {
                x--;
                highlightA[x] = true;
            }
        }
    }

    private static String formatRanges(boolean[] highlights) {
        StringBuilder sb = new StringBuilder();
        int start = -1;
        for (int i = 0; i <= highlights.length; i++) {
            if (i < highlights.length && highlights[i]) {
                if (start == -1) start = i;
            } else {
                if (start != -1) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(start).append("-").append(i);
                    start = -1;
                }
            }
        }
        return sb.length() == 0 ? "." : sb.toString();
    }
}
