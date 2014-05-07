package org.springframework.web.servlet.view.document;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.LocalizedResourceHelper;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.AbstractView;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractPoiExcelView<T extends Workbook> extends AbstractView {
    private String url;
    private String extension;

    protected String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    protected String getUrl() {
        return url;
    }

    /**
     * Set the URL of the Excel workbook source, without localization part nor extension.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    protected boolean generatesDownloadContent() {
        return true;
    }

    /**
     * Creates the workbook from an existing XLS document.
     *
     * @param url     the URL of the Excel template without localization part nor extension
     * @param request current HTTP request
     * @return the HSSFWorkbook
     * @throws Exception in case of failure
     */
    protected T getTemplateSource(String url, HttpServletRequest request) throws Exception {
        LocalizedResourceHelper helper = new LocalizedResourceHelper(getApplicationContext());
        Locale userLocale = RequestContextUtils.getLocale(request);
        Resource inputFile = helper.findLocalizedResource(url, getExtension(), userLocale);

        // Create the Excel document from the source.
        if (logger.isDebugEnabled()) {
            logger.debug("Loading Excel workbook from " + inputFile);
        }

        return createWorkbookFromTemplate(inputFile);
    }

    /**
     * Renders the Excel view, given the specified model.
     */
    @Override
    protected final void renderMergedOutputModel(
            Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {

        T workbook;
        if (getUrl() != null) {
            workbook = getTemplateSource(getUrl(), request);
        } else {
            workbook = createWorkbook();
            logger.debug("Created Excel Workbook from scratch");
        }

        buildExcelDocument(model, workbook, request, response);

        // Set the content type.
        response.setContentType(getContentType());

        // Should we set the content length here?
        // response.setContentLength(workbook.getBytes().length);

        // Flush byte array to servlet output stream.
        ServletOutputStream out = response.getOutputStream();
        workbook.write(out);
        out.flush();
    }

    /**
     * Subclasses must implement this method to create an Excel Workbook document,
     * given the model.
     *
     * @param model    the model Map
     * @param workbook the Excel workbook to complete
     * @param request  in case we need locale etc. Shouldn't look at attributes.
     * @param response in case we need to set cookies. Shouldn't write to it.
     */
    protected abstract void buildExcelDocument(
            Map<String, Object> model, T workbook, HttpServletRequest request, HttpServletResponse response)
            throws Exception;

    protected abstract T createWorkbook();

    protected abstract T createWorkbookFromTemplate(Resource resource) throws IOException;
}
