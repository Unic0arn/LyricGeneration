import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;


public class LyricGeneration {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		
		MaxentTagger tagger = new MaxentTagger(
				"taggers/english-bidirectional-distsim.tagger");
		// The sample string
		BufferedReader reader;
		String sample = "This is a sample text with a few more words to test the speed of our tagger";
		ArrayList<String> lines = new ArrayList<String>();
		ArrayList<String> posTemplates = new ArrayList<String>();
		StringBuilder sb = new StringBuilder();
		
		String line;
		
		
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream("resources/corpus"), "ISO-8859-1"));
			line = reader.readLine();
		
			while(line != null){
				//lines.add(line);
				sb.append(line);
				line = reader.readLine();
			}
			
		
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String tagged = tagger.tagString(sb.toString());
		// Output the result
		 
		System.out.println(tagged);
	}
	public LyricGeneration(){
		
	}
}
