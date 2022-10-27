import javax.swing.*;

public class Test {
	public static void main(String[] args) {
		var textArea = new JTextArea(5, 20);
		JScrollPane scrollPane = new JScrollPane(textArea);
		textArea.setEditable(false);
	}
}
