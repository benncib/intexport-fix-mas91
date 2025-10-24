package custom.iface;

import com.ibm.tivoli.maximo.oslc.provider.PagedDataSerializer;
import com.ibm.tivoli.maximo.oslc.provider.PagedDataSerializerFactory;
import java.rmi.RemoteException;
import java.util.Map;
import psdi.app.system.CrontaskInstanceRemote;
import psdi.app.system.CrontaskParamInfo;
import psdi.iface.mic.PublishChannelCache;
import psdi.iface.mos.ObjectStructureCache;
import psdi.iface.router.Router;
import psdi.mbo.MboSetRemote;
import psdi.security.UserInfo;
import psdi.server.MXServer;
import psdi.server.SimpleCronTask;
import psdi.txn.MXTransaction;
import psdi.util.MXException;
import psdi.util.logging.MXLogger;
import psdi.util.logging.MXLoggerFactory;

public class ExportDataCronTask extends SimpleCronTask {
    protected static final MXLogger integrationLogger = MXLoggerFactory.getLogger("maximo.integration");
    private String qualifiedInstanceName = null;

    public void init() {
    }

    public void start() {
        try {
            this.setSleepTime(0L);
        } catch (Exception e) {
            if (integrationLogger.isErrorEnabled()) {
                integrationLogger.error(e.getMessage(), e);
            }
        }

    }

    public void stop() {
        integrationLogger.info("Export data cron task:" + this.qualifiedInstanceName + " stopped for ");
    }

    public void setCrontaskInstance(CrontaskInstanceRemote inst) {
        try {
            super.setCrontaskInstance(inst);
            String var10001 = inst.getString("crontaskname");
            this.qualifiedInstanceName = var10001 + "." + inst.getString("instancename");
        } catch (Exception e) {
            integrationLogger.error(e.getMessage(), e);
        }

    }

    public void releaseResources() {
    }

    public void cronAction() {
        try {
            String osName = this.getParamAsString("OSNAME");
            String channel = this.getParamAsString("CHANNELNAME");
            String pageSize = this.getParamAsString("PAGESIZE");
            String extSysName = this.getParamAsString("EXTSYSNAME");
            String whereClause = this.getParamAsString("WHERE");
            String format = this.getParamAsString("FORMAT");
            String templateName = this.getParamAsString("TEMPLATENAME");
            String endPointName = this.getParamAsString("ENDPOINTNAME");
            UserInfo userInfo = this.getRunasUserInfo();
            if (channel != null) {
                osName = PublishChannelCache.getInstance().getPublishChannel(channel).getPublishInfo().getMosName();
            }

            String objectName = ObjectStructureCache.getInstance().getMosInfo(osName).getPrimaryMosDetailInfo().getObjectName();
            MboSetRemote msr = MXServer.getMXServer().getMboSet(objectName, userInfo);
            if (whereClause != null) {
                msr.setUserWhere(whereClause);
            }

            if (channel != null) {
                PublishChannelCache.getInstance().getPublishChannel(channel).publishTo(msr, Integer.parseInt(pageSize), extSysName);
                MXTransaction trans = msr.getMXTransaction();
                trans.save();
                trans.commit();
            } else {
                PagedDataSerializer pds = PagedDataSerializerFactory.getPagedDataSerializer(format, msr, osName, templateName, Integer.parseInt(pageSize));
                if (pds.hasNextPage()) {
                    while(pds.hasNextPage()) {
                        byte[] pubMosData = pds.serializeNextPage();
                        if (pubMosData == null) {
                            break;
                        }

                        Router.getHandler(endPointName).invoke((Map)null, pubMosData);
                    }
                }
            }
        } catch (Exception e) {
            integrationLogger.error(e.getMessage(), e);
        }

    }

    public CrontaskParamInfo[] getParameters() throws MXException, RemoteException {
        CrontaskParamInfo[] params = new CrontaskParamInfo[8];
        params[0] = new CrontaskParamInfo();
        params[0].setName("CHANNELNAME");
        params[1] = new CrontaskParamInfo();
        params[1].setName("TEMPLATENAME");
        params[2] = new CrontaskParamInfo();
        params[2].setName("ENDPOINTNAME");
        params[3] = new CrontaskParamInfo();
        params[3].setName("OSNAME");
        params[4] = new CrontaskParamInfo();
        params[4].setName("WHERE");
        params[5] = new CrontaskParamInfo();
        params[5].setName("PAGESIZE");
        params[5].setDefault("1000");
        params[6] = new CrontaskParamInfo();
        params[6].setName("EXTSYSNAME");
        params[7] = new CrontaskParamInfo();
        params[7].setName("FORMAT");
        return params;
    }
}
