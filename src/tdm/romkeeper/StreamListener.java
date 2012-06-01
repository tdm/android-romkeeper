package tdm.romkeeper;

interface StreamListener
{
    void updateBytesRead(int len);
    void updateBytesWritten(int len);
}
