#!/usr/bin/env python3
from bs4 import BeautifulSoup # pip install beautifulsoup4 lxml
from pathlib import Path

enca="utf-8"

resfolder=Path(__file__).absolute().parent.parent.joinpath("app/src/main/res")

referencefolder= resfolder.joinpath("values")




ender = input("write two letter iso code of your country like fr: ")

toTranslateFolder= resfolder.joinpath(f"values-{ender}")

if not toTranslateFolder.is_dir():
    print(f"your folder is not folder or does not exist {toTranslateFolder}")
    exit(0)


def diffmaker(endpath):
    print("=======================================================")
    toCompare = toTranslateFolder.joinpath(endpath)
    reference =  referencefolder.joinpath(endpath)
    if not toCompare.is_file():
        print(f"translation for {endpath} dont exists, coppy it from {reference}")
        return
    
    toCompare = BeautifulSoup(toCompare.open(encoding=enca), "xml")
    reference = BeautifulSoup(reference.open(encoding=enca), "xml")

    def fetchAllNames(src):
        outSet = set()
        for string in src.find_all("string"):
            outSet.add(string["name"])
        return outSet

    toCompare = fetchAllNames(toCompare)
    missing = set()


    for one in fetchAllNames(reference):
        if one not in toCompare:
            missing.add(str(reference.find("string",attrs={"name":one})))
    
    if(len(missing)>0):
        print(f"missing from {endpath} (contains oreginal strings)\n------------------------------------------------------\n"+"\n".join(missing))


diffmaker("strings.xml")

diffmaker("celestial_objects.xml")

