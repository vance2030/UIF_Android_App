
import urllib.request

import torch

import torchvision.models as models

import torchvision.transforms as transforms

from PIL import Image

import numpy as np

import os



print("\n=======================================================")

print("[UIF V6] THE LENS: EXTRACTING REAL IMAGE DATA")

print("=======================================================\n")



# 1. Download a REAL picture of a Golden Retriever (ခွေးပုံ အစစ်)

img_url = "https://raw.githubusercontent.com/pytorch/hub/master/images/dog.jpg"

img_path = "dog.jpg"

if not os.path.exists(img_path):

    print("[*] Downloading Real Photo (Golden Retriever)...")

    urllib.request.urlretrieve(img_url, img_path)



# 2. Load PyTorch ResNet18 (To act as our accurate Feature Extractor)

print("[*] Loading PyTorch Vision Extractor...")

model = models.resnet18(weights=models.ResNet18_Weights.IMAGENET1K_V1)

model.eval()



# 3. Preprocess the Image (Standard AI Image Prep)

img = Image.open(img_path)

preprocess = transforms.Compose([

    transforms.Resize(256), transforms.CenterCrop(224),

    transforms.ToTensor(),

    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),

])

input_tensor = preprocess(img).unsqueeze(0)



# 4. Extract Features (The Causal Math)

print("[*] Extracting 512-Dimensional FP32 Features & 1-Bit Core Inputs...")

with torch.no_grad():

    # Get the exact 512 features that go into the Final Classifier

    features = model.conv1(input_tensor)

    features = model.bn1(features)

    features = model.relu(features)

    features = model.maxpool(features)

    features = model.layer1(features)

    features = model.layer2(features)

    features = model.layer3(features)

    features = model.layer4(features)

    fc_input_tensor = model.avgpool(features).flatten()

    

fc_input_np = fc_input_tensor.numpy().astype(np.float32)



# Generate dummy 1-Bit data for the SIMD core test to keep file size small

# (We are testing the FP32 tail accuracy + SIMD speed together)

simd_input_np = np.random.randint(0, 256, size=4608, dtype=np.uint8)



# 5. Save to Binary File for C++ Engine

bin_file = "real_image_data.bin"

with open(bin_file, "wb") as f:

    f.write(simd_input_np.tobytes()) # First 4608 bytes for SIMD Core

    f.write(fc_input_np.tobytes())   # Next 2048 bytes (512 * 4) for FC Tail



print(f">>> SUCCESS! Real Image Data saved to '{bin_file}'")

print("=======================================================\n")

