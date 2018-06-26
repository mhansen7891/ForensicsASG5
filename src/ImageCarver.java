import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;


public class ImageCarver 
{
	//constants for getNumeric
	public static final char LITTLE = 0;
	public static final char BIG = 1;
	public static final char SIGNED = 0;
	public static final char UNSIGNED = 1;
		
	public static RandomAccessFile file;
	public static String filename;
	public static final long jpegHeader = 0xffd8l;
	public static final long jpegFooter = 0xffd9l;
	public static ArrayList<Image> images;
	
	/**
	 * Structure to represent a potential image in the file
	 * @author michaelhansen
	 *
	 */
	public static class Image
	{
		public long start;
		public long end;
		public long size;
		
		public Image()
		{
			start = end = size = 0;
		}
		
		public  String toString()
		{
			return "Start: " + start + "  End: " + end + "  Size: " + size;
		}
	}
	
	/**
	 * Looks for 0xFFD8 within the image file, starting from the offset passed in.
	 * Returns if a header was found. Adds address of header to new image struct in list of potential images.
	 * Also calls the lookForFooter method to attempt to find the footer that goes with the header.
	 * Returns -1 if no header was found before the end of the file
	 * @param offset
	 * @return
	 * @throws IOException
	 */
	public static long lookForHeader(long offset) throws IOException
	{
		long size = file.length();
		long current = offset;
		long value = 0l;
		
		//search from start offset to end of file
		while(current < size)
		{
			value = getNumeric(file, current, 2, UNSIGNED, BIG);
			
			//look for header bytes
			if(value == jpegHeader)
			{
				//add start address to list
				Image i = new Image();
				i.start = current;
				images.add(i);
				
				//look for corresponding footer
				lookForFooter(current);
				
				//return address of header
				return current; 
			}
			//continue search
			current++;
		}
		return -1l; //-1 to signify no header found
	}
	
	/**
	 * Starting at address header, search through image file for 0xFFD9. If found, add offset to last Image struct in 
	 * list. If no footer is found, remove the last item from the 'images' list.
	 * @param header
	 * @throws IOException
	 */
	public static void lookForFooter(long header) throws IOException
	{
		long size = file.length();
		long current = header;
		long value = 0l;
		
		//search from start offset to end of file
		while(current < size)
		{
			value = getNumeric(file, current, 2, UNSIGNED, BIG);
			
			//search for footer bytes
			if(value == jpegFooter)
			{
				//finish populating image struct
				images.get(images.size() - 1).end = current + 2l;
				images.get(images.size() - 1).size = images.get(images.size() - 1).end - images.get(images.size() - 1).start;
				return;
			}
			//continue search
			current++;
		}
		//if no footer was found, remove image from list
		images.remove(images.size() - 1);
	}
	
	/**
	 * Take contents of jpeg described by image, and write to a new file with name name.
	 * @param image
	 * @param name
	 * @throws IOException
	 */
	public static void writeToFile(Image image, String name) throws IOException
	{
		//create new file
		File imageFile = new File(name + ".jpeg");
		
		//create array to hold data
		byte[] data = new byte[(int) image.size];
	
		file.seek(image.start);
		
		//read data from image file
		file.read(data);
		
		OutputStream os = new FileOutputStream(imageFile);
		
		//write data to file
		os.write(data);
		
		os.close();
	}
	
	/**
	 * Initialize RandomAccessFile of name s and ArrayList of images
	 * @param s
	 */
	public static void init(String s)
	{
		filename = s;
		
		try 
		{
			file = new RandomAccessFile(s, "r");
		} 
		catch (FileNotFoundException e) {e.printStackTrace();}
		
		images = new ArrayList<Image>();
	}
	
	/**
	 * Read between 1 and 8 bytes, in either BIG or LITTLE endian, as either SIGNED or UNSIGNED
	 * into a long
	 * @param f
	 * @param offset
	 * @param size
	 * @param type
	 * @param endian
	 * @return
	 */
	public static long getNumeric(RandomAccessFile f, long offset, long size, char type, char endian)
	{
		if(size < 1)
			return 0;
		
		byte[] bytes = new byte[(int)size]; //array to hold bytes
		boolean isNegative = false;
		
		if(offset < 0)
		{
			System.out.println("Offset must be positive, not " + offset);
			return 0;
		}
		
		try 
		{
			f.seek(offset); //seek to start of number
		} 
		catch (IOException e) {e.printStackTrace();}
			
		try 
		{
			f.read(bytes); //read from file
		} 
		catch (IOException e) {e.printStackTrace();}
		
		//if little endian, array needs to be reversed
		if(endian == LITTLE)
		{
			byte temp;
			
			//reverse byte array
			for(int i = 0; i < bytes.length / 2; i++)
			{
				temp = bytes[i];
				bytes[i] = bytes[bytes.length - i - 1];
				bytes[bytes.length - i - 1] = temp;
			}
		}
		
		//check if negative (if most significant bit is 1)
		if(type == SIGNED && ((bytes[0] >> 7) & 0x1) == 0x1)
		{
			isNegative = true;
		}
		
		long value = 0;
		
		//put bytes into long
		for (int i = 0; i < bytes.length; i++)
		{
		   value = ((value << 8) | (bytes[i] & 0xff));
		}
		
		if(type == SIGNED && isNegative)
		{
			return ((~value) + 0x1); //two's complement
		}
		else
		{
			return value;
		}
	}
	
	public static void main(String[] args) 
	{
		init("unalloc.img");
		
		
		//find first image
		long offset = 0l;
		try 
		{
			offset = lookForHeader(0l);
		} 
		catch (IOException e1) {e1.printStackTrace();}
		
		//find all jpegs in disk image
		while(offset != -1l)
		{
			try 
			{
				offset = lookForHeader(offset + 1l);
			} 
			catch (IOException e) {e.printStackTrace();}
		}
		
		//write all found images to files
		for(int i = 0; i < images.size(); i++)
		{
			try 
			{
				writeToFile(images.get(i), "Image" + i);
			} 
			catch (IOException e) {e.printStackTrace();}
			
			//Print file info
			System.out.printf("File name: %-13s  Start: %-8d  End: %-8d  Size: %-8d \n", "Image" + i + ".jpeg",
					images.get(i).start, images.get(i).end, images.get(i).size);
		}
	}
}
