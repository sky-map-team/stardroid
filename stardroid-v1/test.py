import sys
import os

try:
    import Vision
    import CoreImage
    import Quartz
except ImportError:
    print("PyObjC or Vision framework not found")
    sys.exit(1)

def recognize_text(image_path):
    url = Quartz.NSURL.fileURLWithPath_(image_path)
    image = CoreImage.CIImage.imageWithContentsOfURL_(url)
    if image is None:
        return "Could not load image"
    
    request = Vision.VNRecognizeTextRequest.alloc().init()
    request.setRecognitionLevel_(0) # accurate
    
    handler = Vision.VNImageRequestHandler.alloc().initWithCIImage_options_(image, None)
    success, error = handler.performRequests_error_([request], None)
    
    if success:
        results = request.results()
        text = []
        for obs in results:
            candidates = obs.topCandidates_(1)
            if candidates:
                text.append(candidates[0].string())
        return "\n".join(text)
    return "Error"

for p in sys.argv[1:]:
    print(f"--- {p} ---")
    print(recognize_text(p))

