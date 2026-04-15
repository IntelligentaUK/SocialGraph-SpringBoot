// * 
 // * Copyright 2014 Jacopo Farina.
 //*
 //* Licensed under the Apache License, Version 2.0 (the "License");
 //* you may not use this file except in compliance with the License.
 //* You may obtain a copy of the License at
 //*
 //*      http://www.apache.org/licenses/LICENSE-2.0
 //*
 //* Unless required by applicable law or agreed to in writing, software
 //* distributed under the License is distributed on an "AS IS" BASIS,
 //* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 //* See the License for the specific language governing permissions and
 //* limitations under the License.
 //* //

package com.intelligenta.socialgraph;

import com.intelligenta.socialgraph.exception.SocialGraphException;
import com.intelligenta.socialgraph.util.ImagePayloads;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.imageio.ImageIO;

/**
 * A library to implement seam carving (aka liquid rescale, see https://en.wikipedia.org/wiki/Seam_carving) in pure Java
 * Given an image, it tries to reduce its width keeping as much details as possible.
 * To do so, a simple heuristic chooses the pixels less likely to contain details and removes them.
 *
 */

public class LiquidRescaler {
    private static final float BLUE_HUE = 240f / 360f;
    private static final float RED_HUE = 0f;

    public static void main(String argc[]) throws IOException{
        BufferedImage imgIn = ImageIO.read(new File("input_pic.jpg"));
        int[][] minVarianceValue = getMinVarianceMatrix(imgIn);
        BufferedImage imgVariance =getColorScaleVarianceMatrix(minVarianceValue);
        ImageIO.write(imgVariance, "JPEG", new File("variance_values.jpg"));
        int numPixelsToRemove=1000;
        markBestPaths(minVarianceValue,numPixelsToRemove);
        BufferedImage imgPaths = new BufferedImage(imgIn.getWidth(),imgIn.getHeight(),BufferedImage.TYPE_INT_RGB);
        for(int x=0;x<minVarianceValue.length;x++){
            for(int y=0;y<minVarianceValue[0].length;y++){
                if(minVarianceValue[x][y]==Integer.MAX_VALUE)
                    imgPaths.setRGB(x,y,~imgIn.getRGB(x,y));
                else
                    imgPaths.setRGB(x,y,imgIn.getRGB(x,y));
            }
        }
        ImageIO.write(imgPaths, "JPEG", new File("paths_to_remove.jpg"));
        BufferedImage imgResc=rescaleImage(imgIn,numPixelsToRemove);    
        ImageIO.write(imgResc, "JPEG", new File("rescaled_single_step.jpg"));
        
        //let's scale in small steps, to see the resulting difference
        long before=System.currentTimeMillis();
        imgIn = rescaleImageInSteps(imgIn,numPixelsToRemove,10);
        System.out.println("took "+(System.currentTimeMillis()-before)/1000+"seconds to rescale this image");
        ImageIO.write(imgIn, "JPEG", new File("frame_rescaled_steps.jpg"));
        
    }
    
    
    
    /**
     * Return the cost of a step from one pixel to another
     * This cost is calculated as a weighted average between the gradient magnitude of the to pixel ad the difference of the from and to colors
     */
    private static int stepCost(BufferedImage imgIn, int x1, int y1, int x2, int y2) {
        int to=imgIn.getRGB(x1, y1);
        int from=imgIn.getRGB(x2, y2);
        
        return ((x1<imgIn.getWidth()-2?colorRGBDifference(to,imgIn.getRGB(x1+1, y1)):0)
                +(x1>0?colorRGBDifference(to,imgIn.getRGB(x1-1, y1)):0)
                +(y1<imgIn.getHeight()-2?colorRGBDifference(to,imgIn.getRGB(x1, y1+1)):0)
                +(y1>0?colorRGBDifference(to,imgIn.getRGB(x1, y1-1)):0)
                +5*colorRGBDifference(to,from))/9
                ;
        
    }
    
    /**
     * Return the difference between two RGB colors
     * This value is to number between 0 (same color) and 443 (difference between black and white, since sqrt(3*256^2)=443 )
     */
    private static int colorRGBDifference(int a,int b){
        return (int)Math.sqrt(Math.pow((a & 0xFF) - (b & 0xFF),2)
                + Math.pow(((a & 0xFF00) >> 8) - ((b & 0xFF00) >> 8),2)
                + Math.pow(((a & 0xFF0000) >> 16) - ((b & 0xFF0000) >> 16),2));
    }
    
    /**
     * Return a matrix reporting for each pixel the minimum cost necessary to reach it from the bottom
     * It will be used to look for the less expensive path
     * @param imgIn the BufferedImage to use
     * @return the cumulative cost matrix
     */
    public static int[][] getMinVarianceMatrix(BufferedImage imgIn) {
        int[][] minVarianceValue = new int[imgIn.getWidth()][imgIn.getHeight()];
        //first row, is all 0s
        for(int x=0;x<imgIn.getWidth();x++){
            minVarianceValue[x][imgIn.getHeight()-1]=0;
        }
        int widthMaxIndex=imgIn.getWidth()-1;
        for(int y=imgIn.getHeight()-2;y>0;y--){
            //the two pixels on the edges are different
            minVarianceValue[widthMaxIndex][y]=Math.min(stepCost(imgIn,widthMaxIndex,y+1,widthMaxIndex,y)+minVarianceValue[widthMaxIndex][y+1], stepCost(imgIn,widthMaxIndex-1,y+1,widthMaxIndex,y)+minVarianceValue[widthMaxIndex-1][y+1]);
            for(int x=1;x<imgIn.getWidth()-1;x++){
                minVarianceValue[x][y]=Math.min(stepCost(imgIn,x,y+1,x,y)+minVarianceValue[x][y+1],
                        Math.min(stepCost(imgIn,x-1,y+1,x,y)+minVarianceValue[x-1][y+1],
                                stepCost(imgIn,x+1,y+1,x,y)+minVarianceValue[x+1][y+1])
                );
            }
            minVarianceValue[0][y]=Math.min(stepCost(imgIn,0,y+1,0,y)+minVarianceValue[0][y+1], stepCost(imgIn,1,y+1,0,y)+minVarianceValue[1][y+1]);
        }
        return minVarianceValue;
    }
    
    /**
     * Return an image representing the variance matrix in color scale (colors with hue from blue to red)
     * It's useful to understand how the program decided where to remove the pixels
     * @param minVarianceValue the cumulative variance matrix, which can be obtained by getMinVarianceMatrix
     * @return an RGB BufferedImage with the same size of the matrix, with colors from red (maximum) to blue (minimum)
     */
    public static BufferedImage getColorScaleVarianceMatrix(int[][] minVarianceValue) {
        BufferedImage imgVariance = new BufferedImage(minVarianceValue.length,minVarianceValue[0].length,BufferedImage.TYPE_INT_RGB);
        int maxVariance=0;
        for(int x=0;x<minVarianceValue.length;x++){
            for(int y=0;y<minVarianceValue[0].length;y++){
                if(minVarianceValue[x][y]>maxVariance)
                    maxVariance=minVarianceValue[x][y];
            }
        }
        for(int x=0;x<minVarianceValue.length;x++){
            for(int y=0;y<minVarianceValue[0].length;y++){
                float ratio = maxVariance == 0 ? 0f : (float) minVarianceValue[x][y] / (float) maxVariance;
                float hue = BLUE_HUE + (RED_HUE - BLUE_HUE) * ratio;
                imgVariance.setRGB(x,y,java.awt.Color.HSBtoRGB(hue, 1, 1));
            }
        }
        return imgVariance;
    }
    
    /**
     * Return to vector with the minimum variance path (as an ordered list of x coordinates) based on the given cumulative variance matrix
     * NOTE: the difference between consecutive coordinates is 0,1 or -1 only when to path not crossing Integer.MAX_VALUE cells is possible
     * If not, the path can "jump" to any coordinate not crossing this value
     * This is done to find many paths on the same image always removing the same number of pixels on to row (path cannot cross)
     */
    private static int[] minVariancePath(int[][] minVarianceValue) {
        
        int [] pxlToRemoveindex=new int[minVarianceValue[0].length];
        pxlToRemoveindex[0]=1;
        for(int x=1;x<minVarianceValue.length-1;x++){
            if(minVarianceValue[x][0]<minVarianceValue[pxlToRemoveindex[0]][0] && minVarianceValue[x][0]!=Integer.MAX_VALUE)
                pxlToRemoveindex[0]=x;
        }
        
        for(int y=1;y<minVarianceValue[0].length-1;y++){
            pxlToRemoveindex[y]=pxlToRemoveindex[y-1];
            if(pxlToRemoveindex[y]>0 && minVarianceValue[pxlToRemoveindex[y-1]-1][y]<minVarianceValue[pxlToRemoveindex[y-1]][y])
                pxlToRemoveindex[y]=pxlToRemoveindex[y-1]-1;
            
            
            if(pxlToRemoveindex[y-1]<minVarianceValue.length-1 && minVarianceValue[pxlToRemoveindex[y-1]+1][y]<minVarianceValue[pxlToRemoveindex[y-1]][y])
                pxlToRemoveindex[y]=pxlToRemoveindex[y-1]+1;
            
            if(minVarianceValue[pxlToRemoveindex[y]][y]==Integer.MAX_VALUE){
                //System.out.println("jump!");
                //the path is crossing another path, we need to "jump"
                for(int x=1;x<minVarianceValue.length-1;x++){
                    if(minVarianceValue[x][y]<minVarianceValue[pxlToRemoveindex[y]][y])
                        pxlToRemoveindex[y]=x;
                }
            }
        }
        return pxlToRemoveindex;
    }
    
    /**
     * Mark the n best paths (that is, minimum variance) setting their values to Integer.MAX_VALUE
     * @param minVarianceValue the cumulative variance matrix
     * @param numPaths the number of paths to be marked
     */
    public static void markBestPaths(int[][] minVarianceValue, int numPaths) {
        for(int i=0;i<numPaths;i++){
            int [] pxlToRemoveindex=minVariancePath(minVarianceValue);
            for(int y=1;y<minVarianceValue[0].length;y++){
                minVarianceValue[pxlToRemoveindex[y]][y]=Integer.MAX_VALUE;
            }
        }
    }
    
    /**
     * return to rescaled ARGB image with to width decreased by numPixelsToRemove
     * Pixels with the coordinates of an Integer.MAX_VALUE in minVarianceValue are removed by shifting the others on left
     *
     */
    private static BufferedImage rescaleImage(BufferedImage imgIn, int[][] minVarianceValue,int numPixelsToRemove) {
        BufferedImage imgResc = new BufferedImage(imgIn.getWidth()-numPixelsToRemove,imgIn.getHeight(),BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<minVarianceValue[0].length;y++){
            int survivorIndex=0;
            for(int x=0;x<imgResc.getWidth();x++){
                
                while(minVarianceValue[survivorIndex][y]==Integer.MAX_VALUE)
                    survivorIndex++;
                imgResc.setRGB(x, y, imgIn.getRGB(survivorIndex, y));
                survivorIndex++;
            }
        }
        return imgResc;
    }
    
    /**
     * Applies the liquid rescale to the given image, reducing it by numPixelsToRemove pixels
     * Equivalent to calling rescaleImageInSteps with stepSize equals to numPixelsToRemove
     *
     * @param imgIn the original BufferedImage to be rescaled
     * @param numPixelsToRemove how much to reduce the width, in pixels
     * @return the rescaled RGB image, with a reduced width
     */
    public static BufferedImage rescaleImage(BufferedImage imgIn, int numPixelsToRemove) {
        int[][] minVarianceValue = getMinVarianceMatrix(imgIn);
        markBestPaths(minVarianceValue,numPixelsToRemove);
        return rescaleImage(imgIn,minVarianceValue,numPixelsToRemove);
    }
    
    /**
     * Applies the liquid rescale to the given image, reducing it by numPixelsToRemove pixels
     * it does so iteratively removing stepSize pixels at time.
     * Increasing stepSize reduces the precision but increases the speed.
     *
     * @param imgIn the original BufferedImage to be rescaled
     * @param numPixelsToRemove how much to reduce the width, in pixels
     * @param stepSize how many pixels remove in each step, smaller is more precise but slower, for most uses 10 is a good value
     * @return the rescaled RGB image, with a reduced width
     */
    public static BufferedImage rescaleImageInSteps(BufferedImage imgIn, int numPixelsToRemove,int stepSize) {
        if(numPixelsToRemove <= 0){
            return imgIn;
        }
        stepSize = Math.max(1, stepSize);
        int remaining=numPixelsToRemove;
        while(remaining>0){
            int pixelsThisStep=Math.min(stepSize, remaining);
            imgIn = rescaleImage(imgIn,pixelsThisStep);
            remaining-=pixelsThisStep;
        }
        return imgIn;
    }

    public static RescaledImage rescaleImage(byte[] sourceBytes,
                                             int numPixelsToRemove,
                                             int stepSize,
                                             String preferredMimeType) throws IOException {
        BufferedImage imageIn = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        if (imageIn == null) {
            throw new SocialGraphException("invalid_image_payload", "The supplied image could not be decoded");
        }
        if (numPixelsToRemove >= imageIn.getWidth()) {
            throw new SocialGraphException("invalid_image_payload", "cut must be smaller than the image width");
        }

        BufferedImage imageOut = rescaleImageInSteps(imageIn, numPixelsToRemove, stepSize);
        String outputMimeType = chooseOutputMimeType(preferredMimeType);
        String formatName = formatNameForMimeType(outputMimeType);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(imageOut, formatName, baos);
        if (!written) {
            throw new IOException("No ImageIO writer is available for " + outputMimeType);
        }
        baos.flush();

        return new RescaledImage(
            baos.toByteArray(),
            outputMimeType,
            ImagePayloads.extensionForMimeType(outputMimeType)
        );
    }

    public static byte[] rescaleImageWithURL(String url, int numPixelsToRemove, int stepSize) throws IOException {
         
        URL imageURL = new URL(url);
        
        BufferedImage imageIn = ImageIO.read(imageURL);
        BufferedImage imageOut = rescaleImageInSteps(imageIn,numPixelsToRemove,stepSize);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
	ImageIO.write( imageOut, "jpg", baos);
	baos.flush();
	byte[] imageInByte = baos.toByteArray();
	baos.close();
        return imageInByte;
        
    }

    private static String chooseOutputMimeType(String preferredMimeType) {
        String normalizedMimeType = ImagePayloads.normalizeMimeType(preferredMimeType);
        if (canWriteMimeType(normalizedMimeType)) {
            return normalizedMimeType;
        }
        if (canWriteMimeType("image/png")) {
            return "image/png";
        }
        if (canWriteMimeType("image/jpeg")) {
            return "image/jpeg";
        }
        throw new SocialGraphException("invalid_image_payload", "No compatible image writer is available");
    }

    private static boolean canWriteMimeType(String mimeType) {
        return mimeType != null && ImageIO.getImageWritersByMIMEType(mimeType).hasNext();
    }

    private static String formatNameForMimeType(String mimeType) {
        return switch (ImagePayloads.normalizeMimeType(mimeType)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new SocialGraphException(
                "unsupported_image_type",
                "Only JPEG, PNG, and WebP images are supported"
            );
        };
    }

    public record RescaledImage(byte[] bytes, String mimeType, String extension) {
    }
}
