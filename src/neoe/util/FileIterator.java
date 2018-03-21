package neoe.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FileIterator implements Iterable<File> {

	// private String root;
	List<File> buf;
	private boolean sortByName;

	public FileIterator(String dir) {
		buf = new ArrayList<File>();
		File f = new File(dir);
		buf.add(f);
	}

	public FileIterator(String dir, boolean sortByName) {
		this(dir);
		this.sortByName = sortByName;
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Iterable<File> it = new FileIterator("C:/xxx");
		int total = 0;
		int dircnt = 0;
		int[] linecnt = new int[2];
		Map<String, Integer> cnt = new HashMap<String, Integer>();
		for (File f : it) {
			if (f.isDirectory()) {
				dircnt++;
				continue;
			}
			total++;
			String fn = f.getName();
			int p1 = fn.indexOf(".");
			if (p1 > 0) {
				String ext = fn.substring(p1);
				Integer i = cnt.get(ext);
				if (i == null) {
					cnt.put(ext, 1);
				} else {
					cnt.put(ext, i + 1);
				}
				if (ext.equalsIgnoreCase(".xxx")) {
					getLineCnt(f, linecnt);
				}
			}

			System.out.println(f.getAbsolutePath());

		}
		System.out.println(cnt);
		System.out.println(linecnt[0] + "," + linecnt[1] + "," + (linecnt[1] * 100 / linecnt[0]) + "%");
	}

	private static int getLineCnt(File f, int[] linecnt) throws Exception {
		int cnt = 0;
		BufferedReader in = new BufferedReader(new FileReader(f));
		String line;
		while ((line = in.readLine()) != null) {
			linecnt[0]++;
			if (line.trim().startsWith("'")) {
				linecnt[1]++;
			}
		}
		return cnt;
	}

	@Override
	public Iterator<File> iterator() {
		return new Iterator<File>() {

			@Override
			public boolean hasNext() {
				return buf.size() > 0;
			}

			@Override
			public File next() {
				File f = buf.remove(0);
				if (f.isDirectory()) {
					File[] sub = f.listFiles();
					if (sub!=null){
						if (sortByName) {
							sortFiles(sub);
						}
						buf.addAll(Arrays.asList(sub));
					}
				}
				return f;
			}

			@Override
			public void remove() {
			}
		};
	}

	public static void sortFiles(File[] sub) {
		Arrays.sort(sub, new Comparator<File>() {
			@Override
			public int compare(File o1, File o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		
	}

}
