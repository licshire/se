package se.crawler.framework;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.util.Set;

import se.crawler.url.fontier.LinkFilter;
import se.crawler.url.fontier.RiceLinkFilter;
import se.crawler.url.queue.LinkQueue;

/**
 * 爬取中国水稻网子线程 ---定制爬虫
 * @since 2016.6.15
 * @author pillar
 * @version 1.0
 */
public class CrawlerRice implements Runnable {
	private DownLoadPage crawler = null;
	public static final Object signal = new Object();
	private LinkFilter filter = new RiceLinkFilter();           //定义URL过滤器2
	private int count = 0;        //等待的线程
    private int downNum = 0;      //下载网页的数量
    
	@Override
	public void run() {
		while(true){
			String tmpurl = LinkQueue.getAUnVisitedurl();			
			if(tmpurl!=null){
				String content = null;
				try {
					crawler = new DownLoadPage();
					int d = LinkQueue.geturldepth(tmpurl);      	//爬取深度	
					content = crawler.downloadFile(tmpurl,d);
				    if(content!=null){		    				    	
						System.out.println("爬网页"+tmpurl+"成功，深度为"+d+" 是由线程"+Thread.currentThread().getName()+"来爬");
						LinkQueue.addVisitedUrl(tmpurl);	            //将该 url 放入到已访问的 URL中
					    if(d==1){ 	   									//首页时把所有导航页面的url放进队列
					    	Set<String> links =null;  
					    	if("http://www.rice.agri.cn/"==tmpurl){					    		
					    		links = UrlExtractorRice.getAllRiceNavigationLinks();	
					    		System.out.println("中国水稻网共有: "+links.size()+" 个导航页面!");
					    		for(String link:links){
						            LinkQueue.addUnvisitedUrl(link,d+1); 	  			//所有导航页面入队
					            }
					    	}else{
					    		System.out.println("这是抓取中国水稻网的定制爬虫,您提供的种子url有误: "+tmpurl);
					    	}
				    	}else{				    		
				    		String urlType = "";
				    		if(tmpurl.contains("htm")&&!tmpurl.contains("index")){	//详细页面
					    		urlType = "DetailPage";				    			
					    		saveToLocal(urlType,content,tmpurl);        		//详细页面保存至本地
					    	}else{  				
					    		urlType = "ListPage";
					    		Set<String> links = null;
					    		saveToLocal(urlType,content,tmpurl); 				    
					    		links = UrlExtractorRice.extractLinks(tmpurl,content,filter);
					    		for(String link:links){
						            LinkQueue.addUnvisitedUrl(link,d+1); 			//新提取的详细页面URL入队						            
					            }
					    	}
				    	}					    			            				           				            
				    }
				    if(count>0){    
					    synchronized(signal){								    	
						    count--;
						    signal.notify();   //唤醒睡眠的线程
			            }
		            }					    
				}catch (URISyntaxException e) {				
					e.printStackTrace();
				}
		    }else{
		    	synchronized(signal){  		  //signal为同步监视器
		    		try{
		    			count++; 
		    			System.out.println("现在有"+count+"个线程在等待");
		    			signal.wait();	//当前线程暂停
		    		}catch(InterruptedException e){
		    			e.printStackTrace();
		    		}
		    	}
		    }
		}
	}

	/**
	 * 保存至本地
	 * @author pillar
	 * @param urlType   页面类型
	 * @param content   网页源代码
	 * @param url    	网页url地址
	 */
	private void saveToLocal(String urlType,String content, String url) {
		String path = "";
		String tmppath = "";
		File files = null;
		if(urlType=="DetailPage"){
			if(url.contains("keji")){
				tmppath = "html/rice-kj/DetailPage/";				
			}else if(url.contains("shichang")){
				tmppath = "html/rice-sc/DetailPage/";
			}else if(url.contains("xinwen")){
				tmppath = "html/rice-xw/DetailPage/";
			}else if(url.contains("fuwu")){
				tmppath = "html/rice-fw/DetailPage/";
			}else if(url.contains("wenhua")){
				tmppath = "html/rice-wh/DetailPage/";
			}else{}
			files = new File(tmppath);
			if(!files.exists()){
				files.mkdirs();
			}	
			path = tmppath+getFilePathByUrl(url);    //存放详细页面的路径
		}else if(urlType=="ListPage"){
			if(url.contains("keji")){
				tmppath = "html/rice-kj/ListPage/";				
			}else if(url.contains("shichang")){
				tmppath = "html/rice-sc/ListPage/";
			}else if(url.contains("xinwen")){
				tmppath = "html/rice-xw/ListPage/";
			}else if(url.contains("fuwu")){
				tmppath = "html/rice-fw/ListPage/";
			}else if(url.contains("wenhua")){
				tmppath = "html/rice-wh/ListPage/";
			}else{}
			files = new File(tmppath);
			if(!files.exists()){
				files.mkdirs();
			}	
			path = tmppath+getFilePathByUrl(url);    //存放导航页面的路径
		}else{}
		String result = "<!--url:"+url+"-->"+"\r\n"+content;   //把url地址存入html文件首行
		try {
			byte[] data = result.getBytes("utf-8");  
			DataOutputStream out = new DataOutputStream(new FileOutputStream(new File(path)));
			for (int i = 0; i < data.length; i++){
				out.write(data[i]);
			}
			out.flush();   			//清空缓冲区
			out.close();   
			synchronized(signal){  
		    	downNum++;      	//保存成功后下载数网页数加一
		    }
			System.out.print("SaveToLocal: "+url+"\n");
		}catch(Exception e){
			System.out.print("Fail to save page: "+url+"\n");
			e.printStackTrace();
		}
	}
	/**
	 * 根据url制定文件名
	 * @author pillar
	 * @param url	网页的URL地址
	 * @return	返回处理后可作为文件名的URL片断
	 */
	private String getFilePathByUrl(String url){
		String uri = url.substring(7);	//remove "http://";
		String contentType = crawler.getContentType();
		if(contentType!=null){		
			if(contentType.indexOf("html")!=-1){  //text/html类型
				if(uri.contains("htm")){
					return uri.replaceAll("[\\/:*?\"<>|]", "_");
				}else{
					return uri.replaceAll("[\\/:*?\"<>|]", "_")+".html";
				}
			}else{ //如application/pdf类型				
	            return uri.replaceAll("[\\?/:*|<>\"]", "_")+"."+
	            contentType.substring(contentType.lastIndexOf("/")+1);
			}
		}else{
			if(uri.contains("htm")){
				return uri.replaceAll("[\\/:*?\"<>|]", "_");
			}else{
				return uri.replaceAll("[\\/:*?\"<>|]", "_")+".html";
			}
		}			
	}
	
	public int getCount(){
		return count; 
	}
	public int getDownNum(){
		return downNum;
	}
	
}
