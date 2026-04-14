#!/usr/bin/env python3
from bs4 import BeautifulSoup # pip install beautifulsoup4 lxml
from pathlib import Path

enca="utf-8" #used encoding

#get root of project
resfolder=Path(__file__).absolute().parent.parent.joinpath("app/src/main/res")

#get folder with reference string
referencefolder= resfolder.joinpath("values")

#get language
lang = input("write two letter iso code of your country like fr: ")

#target folder to translate
toTranslateFolder= resfolder.joinpath(f"values-{lang}")

if not toTranslateFolder.is_dir():
    print(f"your folder is not folder or does not exist {toTranslateFolder}")
    exit(0)


def checker(file):
    """
    prints 
    * diff between reference and target translation
    * duplicates in target translation
    """
    print("=======================================================")
    toCompare = toTranslateFolder.joinpath(file)
    reference =  referencefolder.joinpath(file)
    if not toCompare.is_file():
        print(f"translation for {file} dont exists, coppy it from {reference}")
        return
    
    toCompare = BeautifulSoup(toCompare.open(encoding=enca), "xml")
    reference = BeautifulSoup(reference.open(encoding=enca), "xml")

    def fetchAllNames(src):
        # returns iterator
        return map(lambda string: string["name"], src.find_all("string"))

    toCompare = list(fetchAllNames(toCompare))
    errBuffer = ""
    #diff pringing
    for one in set(fetchAllNames(reference)):
        if one not in toCompare:
            errBuffer += str(reference.find("string",attrs={"name":one}))+"\n"
    
    if errBuffer!="":
        errBuffer= f"missing from {file} (contains oreginal strings)\n------------------------------------------------------\n{errBuffer}"

    #duplication detection
    seen = set()
    dupes = {}
    for x in toCompare:
        if x not in seen:
            seen.add(x)
        elif x in dupes:
            dupes[x]+=1
        else:
            dupes[x]=2
    
    if(len(dupes)>0):
        errBuffer+= f"\n\nfound  duplicates in {file} \n\n"
        for key,value in dupes.items():
           errBuffer+= f"<string name=\"{key}\">    is present {value} times  \n" 

    if(errBuffer!=""):
        print(errBuffer)
    else:
        print(f"no fixes needed in {file}")
    
checker("strings.xml")
checker("celestial_objects.xml")
