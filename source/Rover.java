import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.nio.charset.Charset;
import java.util.Date;

import com.pi4j.io.gpio.*;
import com.pi4j.io.serial.*;
import com.pi4j.wiringpi.SoftPwm;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;


public class Rover extends JFrame implements ActionListener, ChangeListener {
    private static final long serialVersionUID = 7035088961629706287L;

    /**
     * GPIO pins for light, alarm, speed and camera
     */
    private static final byte LIGHT_SWITCH = 0, ALARM_SWITCH = 1, CAM_SWITCH = 2, SPEED_PIN = 1;

    /**
     * GUI variables
     */
    private Container container;
    private JButton lightBut, camBut, alarmBut, autoManBut, forwardBut, backBut, leftBut,
            rightBut, stopBut;
    private JLabel tempLabel, lightLabel, CdisLabel, LdisLabel, RdisLabel, alarmLabel,
            driveDirection, camLabel;
    private JSlider speedSlide;

    /**
     * serial handler
     */
    private final com.pi4j.io.serial.Serial SERIAL = SerialFactory.createInstance();

    /**
     * Threads that control camera and auto-avoidance
     */
    private final Thread camera, avoidance;

    private final GpioController GPIO = GpioFactory.getInstance();
    private final GpioPinDigitalOutput motorLeft1, motorLeft2, motorRight1, motorRight2;

    /**
     * instruction code to control camera
     */
    private final String startInstruction = "/usr/bin/raspivid -t 0 -h 720 -w 1280 -o /home/pi/Videos/";
    private final String endInstruction = "killall raspivid";

    /**
     * if the avoidance function is on
     */
    private boolean onAvoid = true;

    /**
     * variables that indicates temperature and surrounding distances
     */
    private int temperature, Cdistance, Ldistance, Rdistance;


    public static void main(String[] args) {
        //	initiate the program
        try {
            new Rover();
        } catch (IOException io) {
            io.printStackTrace();
        }
    }


    private Rover() throws IOException {
        //	add shutdown hook
        shutDown();

        //	initiate GUI
        speedSlide = new JSlider(SwingConstants.VERTICAL, 0, 5, 5);
        speedSlide.setPaintTicks(true);
        speedSlide.setMajorTickSpacing(1);
        speedSlide.setSnapToTicks(true);
        speedSlide.addChangeListener(this);
        speedSlide.setEnabled(false);

        autoManBut = new JButton("AUTO/MANUAL");
        autoManBut.addActionListener(this);
        alarmBut = new JButton("ON/OFF");
        alarmBut.addActionListener(this);
        lightBut = new JButton("ON/OFF");
        lightBut.addActionListener(this);
        camBut = new JButton("ON/OFF");
        camBut.addActionListener(this);
        forwardBut = new JButton("FORWARD");
        forwardBut.addActionListener(this);
        forwardBut.setEnabled(false);
        backBut = new JButton("BACK");
        backBut.addActionListener(this);
        backBut.setEnabled(false);
        leftBut = new JButton("LEFT");
        leftBut.addActionListener(this);
        leftBut.setEnabled(false);
        rightBut = new JButton("RIGHT");
        rightBut.addActionListener(this);
        rightBut.setEnabled(false);
        stopBut = new JButton("STOP");
        stopBut.addActionListener(this);
        stopBut.setEnabled(false);

        tempLabel = new JLabel("Temperature: ");
        lightLabel = new JLabel("Light OFF");
        alarmLabel = new JLabel("Alarm OFF");
        CdisLabel = new JLabel("Center Distance: ");
        LdisLabel = new JLabel("Left Distance: ");
        RdisLabel = new JLabel("Right Distance: ");
        driveDirection = new JLabel("STOP");
        driveDirection.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        camLabel = new JLabel("Camera Rotation ON");

        Box drive = Box.createVerticalBox(), distance = Box.createVerticalBox(),
                status = Box.createVerticalBox(), environment = Box.createVerticalBox();

        Box controlBox = Box.createHorizontalBox();

        JPanel directionPanel = new JPanel(new GridLayout(3, 3));
        directionPanel.add(new JLabel());
        directionPanel.add(forwardBut);
        directionPanel.add(new JLabel());
        directionPanel.add(leftBut);
        directionPanel.add(stopBut);
        directionPanel.add(rightBut);
        directionPanel.add(new JLabel());
        directionPanel.add(backBut);
        directionPanel.add(new JLabel());

        controlBox.add(speedSlide);
        controlBox.add(Box.createHorizontalStrut(10));
        controlBox.add(directionPanel);
        controlBox.add(Box.createHorizontalStrut(10));

        drive.add(autoManBut);
        drive.add(Box.createVerticalStrut(10));
        drive.add(controlBox);
        drive.add(Box.createVerticalStrut(10));
        drive.add(driveDirection);
        drive.add(Box.createVerticalStrut(10));

        distance.add(Box.createVerticalStrut(10));
        distance.add(CdisLabel);
        distance.add(Box.createVerticalStrut(10));
        distance.add(LdisLabel);
        distance.add(Box.createVerticalStrut(10));
        distance.add(RdisLabel);

        JPanel alarmPanel = new JPanel(new FlowLayout(10)),
                lightPanel = new JPanel(new FlowLayout(10)),
                camPanel = new JPanel(new FlowLayout(10));

        alarmPanel.add(alarmBut);
        alarmPanel.add(alarmLabel);
        lightPanel.add(lightBut);
        lightPanel.add(lightLabel);
        camPanel.add(camBut);
        camPanel.add(camLabel);

        status.add(Box.createVerticalStrut(10));
        status.add(alarmPanel);
        status.add(Box.createVerticalStrut(10));
        status.add(lightPanel);
        status.add(Box.createVerticalStrut(10));
        status.add(camPanel);

        environment.add(tempLabel);
        environment.add(Box.createVerticalStrut(10));

        container = this.getContentPane();
        GridLayout layout = new GridLayout(2, 2);
        layout.setHgap(20);
        layout.setVgap(20);
        container.setLayout(layout);
        container.add(status);
        container.add(distance);
        container.add(environment);
        container.add(drive);

        //	start serial receive
        SERIAL.addListener(new ReceiveData());
        SerialConfig config = new SerialConfig();
        config.device("/dev/ttyACM0")
                .baud(Baud._9600)
                .dataBits(DataBits._8)
                .parity(Parity.NONE)
                .stopBits(StopBits._1)
                .flowControl(FlowControl.NONE);
        SERIAL.open(config);

        //	configure motor driver
        motorLeft1 = GPIO.provisionDigitalOutputPin(RaspiPin.GPIO_22, PinState.LOW);
        motorLeft2 = GPIO.provisionDigitalOutputPin(RaspiPin.GPIO_21, PinState.LOW);
        motorRight1 = GPIO.provisionDigitalOutputPin(RaspiPin.GPIO_27, PinState.LOW);
        motorRight2 = GPIO.provisionDigitalOutputPin(RaspiPin.GPIO_25, PinState.LOW);
        SoftPwm.softPwmCreate(SPEED_PIN, 0, 255);
        SoftPwm.softPwmWrite(SPEED_PIN, 255);

        //	start camera thread
        camera = new Thread(new Camera());
        camera.start();

        //	start auto avoidance thread
        avoidance = new Thread(new Avoidance());
        avoidance.start();

        //	start GUI
        this.setSize(new Dimension(700, 700));
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setFocusable(true);
        this.setVisible(true);
    }


    /**
     * change motor to turn right position and wait 0.5 second
     *
     * @throws InterruptedException
     */
    private final void turnRight() {
        motorRight1.low();
        motorRight2.high();
        motorLeft1.high();
        motorLeft2.low();
        driveDirection.setText("RIGHT");
    }


    /**
     * change motor to turn left position and wait 0.5 second
     *
     * @throws InterruptedException
     */
    private final void turnLeft() {
        motorRight1.high();
        motorRight2.low();
        motorLeft1.low();
        motorLeft2.high();
        driveDirection.setText("LEFT");
    }


    /**
     * change motor to drive forward position
     */
    private final void forward() {
        motorRight1.high();
        motorRight2.low();
        motorLeft1.high();
        motorLeft2.low();
        driveDirection.setText("FORWARD");
    }


    /**
     * change motor to back off position and wait 0.5 second
     *
     * @throws InterruptedException
     */
    private final void backOff() {
        motorRight1.low();
        motorRight2.high();
        motorLeft1.low();
        motorLeft2.high();
        driveDirection.setText("BACKOFF");
    }


    /**
     * stop the motor
     */
    private final void stop() {
        motorRight1.low();
        motorRight2.low();
        motorLeft1.low();
        motorLeft2.low();
        driveDirection.setText("STOP");
    }


    /**
     * change motor speed (0-255)
     *
     * @param speed target motor speed level (0-5)
     */
    private final void changeSpeed(int level) {
        SoftPwm.softPwmWrite(SPEED_PIN, level * 51);
    }


    /**
     * execute the given command
     *
     * @param cmd the command to be executed
     */
    private final void executeCommand(String cmd) {
        try {
            Runtime.getRuntime().exec(cmd);
        } catch (IOException io) {
            io.printStackTrace();
        }
    }


    /**
     * send specified data through serial
     *
     * @param data data to be sent
     */
    private final void sendData(byte data) {
        try {
            SERIAL.write(data);
        } catch (IOException io) {
            io.printStackTrace();
        }
    }


    /**
     * Receive data from serial port
     */
    private final class ReceiveData implements SerialDataEventListener {

        /**
         * 0: center distance
         * 1: front-left distance
         * 2: front-right distance
         * 3: temperature
         */
        private String[] data;

        @Override
        public void dataReceived(SerialDataEvent event) {

            try {
                //	receive data
                data = event.getString(Charset.defaultCharset()).trim().replaceAll("\\s", "").split(":");

                for (int n = 0; n < data.length; n++) {

                    switch (data[n]) {

                        //	distance data
                        case "d":
                            Cdistance = Integer.parseInt(data[n + 1]);
                            Ldistance = Integer.parseInt(data[n + 2]);
                            Rdistance = Integer.parseInt(data[n + 3]);

                            CdisLabel.setText("Center Distance: " + Cdistance);
                            LdisLabel.setText("Left Distance: " + Ldistance);
                            RdisLabel.setText("Right Distance: " + Rdistance);

                            n += 3;
                            break;

                        //	temperature data
                        case "t":
                            temperature = Integer.parseInt(data[n + 1]);
                            tempLabel.setText("Temperature: " + temperature);

                            n++;
                            break;

                        //	front light status
                        case "L":
                            int litStatus = Integer.parseInt(data[n + 1]);

                            if (litStatus == 0) {
                                lightLabel.setText("Light OFF");
                            } else if (litStatus == 1) {
                                lightLabel.setText("Light ON");
                            }

                            n++;
                            break;

                        //	alarm status
                        case "A":
                            int alarmStatus = Integer.parseInt(data[n + 1]);

                            if (alarmStatus == 0) {
                                alarmLabel.setText("Alarm OFF");
                            } else if (alarmStatus == 1) {
                                alarmLabel.setText("Alarm ON");
                            }

                            n++;
                            break;

                        //	camera rotation status
                        case "C":
                            int camStatus = Integer.parseInt(data[n + 1]);

                            if (camStatus == 0) {
                                camLabel.setText("Camera Rotation OFF");
                            } else if (camStatus == 1) {
                                camLabel.setText("Camera Rotation ON");
                            }

                            n++;
                            break;

                    }


                }

            } catch (IOException io) {
                io.printStackTrace();
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            }

        }

    }


    private final class Camera implements Runnable {

        @Override
        public void run() {
            ArrayList<String> fileNames = new ArrayList<String>();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            Date date;
            String fileName;

            try {

                while (true) {
                    //	name video file with date and time
                    date = new Date();
                    fileName = "vid-" + dateFormat.format(date) + ".h264";

                    //	start recording
                    executeCommand(startInstruction + fileName);
                    fileNames.add(fileName);

                    //	wait 30 minutes
                    Thread.sleep(1800000);

                    //	end recording
                    executeCommand(endInstruction);

                    //	if there is more than 10 files, delete the earliest one
                    if (fileNames.size() > 10) {
                        executeCommand("rm -rf /home/pi/Videos/" + fileNames.get(0));
                    }

                    Thread.sleep(100);
                }

            } catch (InterruptedException itr) {
                executeCommand(endInstruction);
            }

        }

    }    //	end Camera


    private final class Avoidance implements Runnable {

        @Override
        public void run() {

            try {

                while (true) {
                    while (onAvoid) {
                        //	if obstacle in front, check other route
                        if (Cdistance < 15 || Ldistance <= 2 || Rdistance <= 2) {
                            stop();

                            //	if no way to go, back off
                            if (!checkRoute()) {

                                back();

                            }

                        } else {
                            //	forward if there is no obstacle in front
                            forward();
                        }

                        Thread.sleep(50);
                    }

                    Thread.sleep(50);
                }

            } catch (InterruptedException itr) {
            }

        }


        private final boolean checkRoute() throws InterruptedException {
            boolean left = false, right = false;

            //	check left status
            if (Ldistance >= 5 && Ldistance <= 2000) {
                left = true;
            }

            //	check right status
            if (Rdistance >= 5 && Rdistance <= 2000) {
                right = true;
            }

            //	if right available, turn right
            if (left && Rdistance <= Ldistance) {
                turnRight();
                Thread.sleep(500);
            }
            //	if only left available, turn left
            else if (right && Ldistance <= Rdistance) {
                turnLeft();
                Thread.sleep(500);
            }
            //	if no available direction to turn
            else {

                return false;

            }

            return true;
        }


        private final void back() throws InterruptedException {

            while (true) {

                //	start back off
                backOff();
                Thread.sleep(200);

                //	if there is a way to go in front, stop backing off
                if (checkRoute()) {
                    break;
                }

            }

        }

    }


    @Override
    public void actionPerformed(ActionEvent e) {

        //	actions when press different buttons
        if (e.getSource() == lightBut) {

            sendData(LIGHT_SWITCH);

        } else if (e.getSource() == camBut) {

            sendData(CAM_SWITCH);

        } else if (e.getSource() == alarmBut) {

            sendData(ALARM_SWITCH);

        } else if (e.getSource() == autoManBut) {

            if (onAvoid == true) {
                onAvoid = false;
                stop();
                forwardBut.setEnabled(true);
                backBut.setEnabled(true);
                leftBut.setEnabled(true);
                rightBut.setEnabled(true);
                stopBut.setEnabled(true);
                speedSlide.setEnabled(true);
            } else {
                onAvoid = true;
                forwardBut.setEnabled(false);
                backBut.setEnabled(false);
                leftBut.setEnabled(false);
                rightBut.setEnabled(false);
                stopBut.setEnabled(false);
                speedSlide.setEnabled(false);
            }

        } else if (e.getSource() == forwardBut) {

            forward();

        } else if (e.getSource() == backBut) {

            backOff();

        } else if (e.getSource() == leftBut) {

            turnLeft();

        } else if (e.getSource() == rightBut) {

            turnRight();

        } else if (e.getSource() == stopBut) {

            stop();

        }

    }


    /**
     * handle shut down operations, shut down GPIO controls
     */
    private final void shutDown() {
        Runtime run = Runtime.getRuntime();

        run.addShutdownHook(new Thread() {
            @Override
            public void run() {
                motorLeft1.low();
                motorLeft2.low();
                motorRight1.low();
                motorRight2.low();

                SoftPwm.softPwmStop(SPEED_PIN);

                camera.interrupt();
                avoidance.interrupt();

                GPIO.shutdown();
            }
        });

    }


    @Override
    public void stateChanged(ChangeEvent e) {

        if (e.getSource() == speedSlide) {
            changeSpeed(speedSlide.getValue());
        }

    }

}
