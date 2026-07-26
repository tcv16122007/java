package com.java.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/uploads/*")
public class FileServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.sendError(404);
            return;
        }
        String filePath = getServletContext().getRealPath("/uploads" + pathInfo);
        File file = new File(filePath);
        if (!file.exists()) {
            resp.sendError(404);
            return;
        }
        String mimeType = getServletContext().getMimeType(filePath);
        if (mimeType == null) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mimeType = "image/jpeg";
            else if (name.endsWith(".png")) mimeType = "image/png";
            else if (name.endsWith(".gif")) mimeType = "image/gif";
            else if (name.endsWith(".webp")) mimeType = "image/webp";
            else if (name.endsWith(".bmp")) mimeType = "image/bmp";
            else if (name.endsWith(".svg")) mimeType = "image/svg+xml";
            else mimeType = "application/octet-stream";
        }
        resp.setContentType(mimeType);
        resp.setContentLengthLong(file.length());
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = resp.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }
}