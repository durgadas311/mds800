import javax.swing.JMenuItem;

public interface LEDHandler {
	LED registerLED(String drive);
	LED registerLED(String drive, LED.Colors color);
	LED[] registerLEDs(String drive, int num, LED.Colors[] colors);
	void setMenuItem(String drive, JMenuItem mi);
}
