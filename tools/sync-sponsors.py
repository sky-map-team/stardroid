import os
import requests
import xml.etree.ElementTree as ET
from xml.dom import minidom
import sys

# Configuration
# Tip: Store your token in your shell profile as BMC_TOKEN
ACCESS_TOKEN = os.getenv("BMC_TOKEN")
OUTPUT_PATH = "../app/src/main/res/values/notranslate-sponsors.xml"
API_URL = "https://developers.buymeacoffee.com/api/v1/supporters"

def fetch_sponsors():
    if not ACCESS_TOKEN:
        print("Error: BMC_TOKEN environment variable not set.")
        sys.exit(1)
        
    headers = {"Authorization": f"Bearer {ACCESS_TOKEN}"}
    try:
        response = requests.get(API_URL, headers=headers)
        response.raise_for_status()
        data = response.json()
        print(response)
        print(data) 
        # Get payer names, filtering out empty or anonymous entries
        return [s.get('payer_name').strip() for s in data.get('data', []) if s.get('payer_name')]
    except Exception as e:
        print(f"Failed to fetch sponsors: {e}")
        sys.exit(1)

def update_xml(sponsors):
    # Join into comma-separated string
    sponsors_str = ", ".join(sponsors)
    
    # Create the XML structure
    resources = ET.Element("resources")
    resources.append(ET.Comment(" A comma-separated list of sponsors "))
    
    string_elem = ET.SubElement(resources, "string", name="sponsors_text")
    # Match the specific requested indentation/format
    string_elem.text = f"\n        {sponsors_str}\n    "

    # Convert to pretty-printed XML
    xml_bytes = ET.tostring(resources, encoding='utf-8')
    pretty_xml = minidom.parseString(xml_bytes).toprettyxml(indent="    ", encoding="utf-8")

    # Ensure the directory exists
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)

    with open(OUTPUT_PATH, "wb") as f:
        f.write(pretty_xml)
    
    print(f"Successfully wrote {len(sponsors)} sponsors to {OUTPUT_PATH}")

if __name__ == "__main__":
    sponsor_list = fetch_sponsors()
    if sponsor_list:
        update_xml(sponsor_list)
    else:
        print("No sponsors found.")

