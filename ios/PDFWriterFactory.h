#include <stdio.h>
#include <PDFWriter.h>
#include <PDFPage.h>
#include <PageContentContext.h>
#include <PDFModifiedPage.h>

//#include <React/RCTBridge.h>
@class RCTBridge;

class PDFWriterFactory {
private:
    PDFWriter* pdfWriter;
    RCTBridge* bridge;
    PDFWriterFactory (PDFWriter*, RCTBridge*);
    void addPages    (NSArray* pageActions);
    void modifyPages (NSArray* pageActions);

public:
    static NSString* create (NSDictionary* pages, RCTBridge* bridge);
    static NSString* modify (NSDictionary* pages, RCTBridge* bridge);
};
