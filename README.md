SmartWaste - Garbage Detection App
SmartWaste is an Android application that uses real-time camera to detect and classify garbage using machine learning technology. The app can identify three types of waste: Organic, Inorganic, and B3 (Hazardous and Toxic Materials).
🚀 Key Features

- Real-time waste monitoring
- Route optimization for waste collection
- Data analytics and reporting
- User and admin dashboards
- Notification system

🏗️ Application Architecture
Main Components

MainActivity.java

- Manages camera lifecycle and detection process
- Converts ImageProxy (YUV420) to Bitmap
- Handles image rotation and preprocessing
- Coordinates between camera and API

BoundingBoxOverlay.java

- Custom View for drawing bounding boxes
- Handles coordinate transformation based on rotation
- Accurate scaling and positioning

RoboflowAPI.java

- Interface to Roboflow API
- Handles HTTP requests and response parsing
- Manages API authentication and error handling
- Converts JSON response to Prediction objects


🔧 Technical Implementation
Image Processing Pipeline

- Camera Capture: Uses CameraX for real-time image capture
- Format Conversion: Converts YUV_420_888 to Bitmap with multiple fallback methods:
	
- Primary: NV21 format conversion with JPEG compression
- Alternative: Direct YUV to RGB conversion
- Fallback: Grayscale conversion from Y plane only


Image Preprocessing:

	Handles rotation compensation (0°, 90°, 180°, 270°)
	Maintains aspect ratio during resize to 416x416
	Base64 encoding for API transmission


Detection Workflow
	
 	Camera Frame → YUV to Bitmap → Rotation & Resize → Base64 → API Call → Parse Response → Display Results
	Coordinate Transformation
	The application handles complex coordinate transformations to properly display bounding boxes:

	Accounts for image rotation from camera sensor
	Scales coordinates from inference resolution to display resolution
	Applies proper offset calculations for centered display

📋 Requirements Dependencies

- Android SDK: Minimum API level 21 (Android 5.0)
- CameraX: For camera functionality
- OkHttp3: For API communication
- JSON: For API response parsing

Permissions
```
xml<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

🚀 Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/stevanza/SmartWaste.git
   cd smartwaste

2. Open in Android Studio

Import the project in Android Studio
Sync the project with Gradle

3. Configure API

Update API_KEY in RoboflowAPI.java with your Roboflow API key
Verify API_URL points to your trained model endpoint

4. Build and Run

Connect your Android device or start an emulator
Build and install the app

🎯 Usage

- Launch the app and grant camera permissions
- Point the camera at waste objects
- View real-time detection with bounding boxes and confidence scores
- Check detection results in the scrollable text area below

📊 Detection Classes

ClassDescriptionColorOrganikOrganic waste (food scraps, leaves, etc.)GreenAnorganikInorganic waste (plastic, metal, glass, etc.)GreenB3Hazardous waste (batteries, chemicals, etc.)Green

⚙️ Configuration
Detection Parameters

- Frame Analysis Interval: 1.5 seconds between detections
- Input Image Size: 416x416 pixels
- Confidence Threshold: Handled by the trained model
- Camera Resolution: 640x640 pixels

Performance Optimization

- Backpressure Strategy: Keeps only the latest frame for analysis
- Thread Management: Uses dedicated executor for camera operations
- Memory Management: Proper bitmap recycling and resource cleanup

🔍 Troubleshooting
Common Issues

Camera Permission Denied

- Ensure camera permission is granted in app settings

API Connection Failed

- Check internet connectivity
- Verify API key and endpoint URL
- Check Roboflow API quota

Detection Not Working

- Ensure good lighting conditions
- Check if objects are clearly visible
- Verify model is trained for your use case


Image Conversion Errors

- Check device compatibility with YUV_420_888 format
- Review logs for specific conversion method failures



📝 Code Structure
app/src/main/java/com/example/smartwaste/
├── MainActivity.java              # Main activity with camera logic
├── BoundingBoxOverlay.java        # Custom view for bounding boxes
└── api/
    └── RoboflowAPI.java          # API interface and utilities

app/src/main/res/
├── layout/
│   └── activity_main.xml         # Main UI layout
└── values/
    └── strings.xml               # String resources

  
🤝 Contributing

	Fork the repository
	Create a feature branch (git checkout -b feature/new-feature)
	Commit your changes (git commit -am 'Add new feature')
	Push to the branch (git push origin feature/new-feature)
	Create a Pull Request


Roboflow for providing the machine learning API
CameraX team for the excellent camera library
OkHttp for reliable HTTP client implementation


Note: This app requires a trained model on Roboflow platform. Make sure to train your model with appropriate garbage dataset before using this application.
