import os
import requests
import xml.etree.ElementTree as ET
from xml.dom import minidom
import sys

# Configuration
ACCESS_TOKEN = os.getenv("BMC_TOKEN")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_PATH = os.path.join(SCRIPT_DIR, "../app/src/main/res/values/notranslate-sponsors.xml")
API_URL = "https://developers.buymeacoffee.com/api/v1/supporters"

def fetch_all_sponsors():
    if not ACCESS_TOKEN:
        print("Error: BMC_TOKEN environment variable not set.")
        sys.exit(1)

    headers = {"Authorization": f"Bearer {ACCESS_TOKEN}"}
    all_sponsors = []
    current_page = 1

    print("Fetching sponsors from Buy Me a Coffee...")

    while True:
        try:
            # Append the page parameter to the request
            response = requests.get(f"{API_URL}?page={current_page}", headers=headers)
            response.raise_for_status()
            res_data = response.json()

            # Extract names from current page
            page_data = res_data.get('data', [])
            names = [s.get('payer_name').strip() for s in page_data
                     if s.get('payer_name') and s.get('payer_name').strip() != 'Someone']
            all_sponsors.extend(names)

            # Check if there are more pages
            last_page = res_data.get('last_page', 1)
            if current_page >= last_page:
                break

            current_page += 1
        except Exception as e:
            print(f"Failed to fetch page {current_page}: {e}")
            break

    return all_sponsors

def update_xml(sponsors):
    # Join into comma-separated string
    sponsors_str = ", ".join(sponsors).replace("'", "\\'")

    # Create the XML structure
    resources = ET.Element("resources")
    resources.append(ET.Comment(" A comma-separated list of sponsors "))

    string_elem = ET.SubElement(resources, "string", name="sponsors_text")
    string_elem.text = f"\n        {sponsors_str}\n    "

    # Convert to pretty-printed XML
    xml_bytes = ET.tostring(resources, encoding='utf-8')
    pretty_xml = minidom.parseString(xml_bytes).toprettyxml(indent="    ", encoding="utf-8")

    # Ensure the directory exists
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)

    with open(OUTPUT_PATH, "wb") as f:
        f.write(pretty_xml)

    print(f"Successfully wrote {len(sponsors)} total sponsors to {OUTPUT_PATH}")

if __name__ == "__main__":
    all_names = fetch_all_sponsors()
    if all_names:
        update_xml(all_names)
    else:
        print("No sponsors found.")

